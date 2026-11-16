package com.example.apartment.modules.repair;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.housing.CurrentResidencyQuery;
import com.example.apartment.modules.identity.*;
import com.example.apartment.support.audit.AuditWriter;
import com.example.apartment.support.storage.RepairImageStorage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import static com.example.apartment.modules.repair.RepairViews.*;

@Service
public class RepairService {
    private final RepairMapper repairs;
    private final AccountAccess accounts;
    private final CurrentResidencyQuery housing;
    private final RepairImageStorage storage;
    private final AuditWriter audit;
    public RepairService(RepairMapper repairs, AccountAccess accounts, CurrentResidencyQuery housing, RepairImageStorage storage, AuditWriter audit) {
        this.repairs = repairs; this.accounts = accounts; this.housing = housing; this.storage = storage; this.audit = audit;
    }
    @Transactional
    public Detail submit(AccountPrincipal actor, Category category, String description, String requestId, List<MultipartFile> files) {
        if (description.isBlank() || description.length() > 2000 || !requestId.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "请填写 1–2000 字的问题描述并提供有效请求标识");
        }
        description = description.strip(); requestId = requestId.toLowerCase(Locale.ROOT);
        var images = storage.prepare(files);
        String hash = fingerprint(category, description, images);
        // Same global lock as checkout, transfer and deactivation. Read the residence only after locking.
        var author = accounts.lockBusinessActor(actor, Role.STUDENT);
        var previous = repairs.previous(actor.id(), requestId);
        if (previous != null) {
            if (!hash.equals(repairs.requestHash(previous))) throw new ApiException(HttpStatus.CONFLICT, "REQUEST_REUSED", "该提交标识已用于不同内容，请重新发起报修");
            return detail(actor, previous);
        }
        var residence = housing.requireForStudent(actor);
        repairs.insert(actor.id(), residence, category, description, requestId, hash);
        long id = repairs.insertedId();
        for (var image : images) repairs.insertImage(id, storage.save(image), image.png().length);
        repairs.log(id, null, Status.PENDING_ASSIGNMENT, author.id(), author.displayName(), "提交报修");
        var after = detail(actor, id);
        audit.record(actor.id(), "REPAIR_SUBMITTED", "REPAIR", id, null, after);
        return after;
    }
    @Transactional(readOnly = true)
    public Page list(AccountPrincipal actor, String q, Status status, int page, int size) {
        requireReader(actor);
        if (q.length() > 64 || page < 1 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "关键词最多 64 字，页码至少为 1，每页 1–100 条");
        Long owner = actor.role() == Role.STUDENT ? actor.id() : null;
        Long assignee = actor.role() == Role.MAINTAINER ? actor.id() : null;
        return new Page(repairs.list(owner, assignee, q.strip(), status, size, (long) (page - 1) * size), repairs.count(owner, assignee, q.strip(), status), page, size);
    }
    @Transactional(readOnly = true)
    public AccountAccess.MaintainerPage maintainers(AccountPrincipal actor, String q, int page, int size) {
        if (actor.role() != Role.SYSTEM_ADMIN && actor.role() != Role.DORM_MANAGER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有派单权限");
        }
        return accounts.enabledMaintainers(q, page, size);
    }
    @Transactional
    public Detail assign(AccountPrincipal actor, long id, long assigneeId, long version) {
        var author = accounts.lockBusinessActor(actor, Role.SYSTEM_ADMIN, Role.DORM_MANAGER);
        var before = authorized(actor, id);
        requireVersionAndStatus(before, version, Status.PENDING_ASSIGNMENT);
        var worker = accounts.lockEnabledMaintainer(assigneeId);
        if (repairs.assign(before, worker, author) != 1) throw changed();
        return recordWorkflow(actor, author, before, "REPAIR_ASSIGNED", "分配维修人员");
    }
    @Transactional
    public Detail reassign(AccountPrincipal actor, long id, long assigneeId, long version, String reason) {
        var author = accounts.lockBusinessActor(actor, Role.SYSTEM_ADMIN, Role.DORM_MANAGER);
        var before = authorized(actor, id);
        requireVersionAndStatus(before, version, Status.PENDING);
        if (Objects.equals(before.assigneeId(), assigneeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "SAME_ASSIGNEE", "请选择另一位维修人员");
        }
        var worker = accounts.lockEnabledMaintainer(assigneeId);
        if (repairs.assign(before, worker, author) != 1) throw changed();
        return recordWorkflow(actor, author, before, "REPAIR_REASSIGNED", reason.strip());
    }
    @Transactional
    public Detail start(AccountPrincipal actor, long id, long version) {
        var author = accounts.lockBusinessActor(actor, Role.MAINTAINER);
        var before = authorized(actor, id);
        requireVersionAndStatus(before, version, Status.PENDING);
        if (repairs.start(id, version) != 1) throw changed();
        return recordWorkflow(actor, author, before, "REPAIR_STARTED", "开始维修");
    }
    @Transactional
    public Detail complete(AccountPrincipal actor, long id, long version, String completionNote) {
        var author = accounts.lockBusinessActor(actor, Role.MAINTAINER);
        var before = authorized(actor, id);
        requireVersionAndStatus(before, version, Status.IN_PROGRESS);
        if (repairs.complete(id, version, completionNote.strip()) != 1) throw changed();
        return recordWorkflow(actor, author, before, "REPAIR_COMPLETED", completionNote.strip());
    }
    private Detail recordWorkflow(AccountPrincipal actor, AccountView author, Order before, String action, String note) {
        repairs.workflowLog(before, repairs.find(before.id()), author, note);
        var after = detail(actor, before.id());
        audit.record(actor.id(), action, "REPAIR", before.id(), before, after);
        return after;
    }
    private void requireVersionAndStatus(Order order, long version, Status status) {
        if (order.version() != version || order.status() != status) throw changed();
    }
    private ApiException changed() {
        return new ApiException(HttpStatus.CONFLICT, "REPAIR_CHANGED", "工单状态或负责人已变化，请刷新详情后重试");
    }
    @Transactional
    public Detail cancel(AccountPrincipal actor, long id, String reason) {
        var author = accounts.lockBusinessActor(actor, Role.STUDENT, Role.DORM_MANAGER, Role.SYSTEM_ADMIN);
        var before = authorized(actor, id);
        if ((before.status() != Status.PENDING_ASSIGNMENT && before.status() != Status.PENDING) || repairs.cancel(id) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "REPAIR_CHANGED", "工单状态已变化，只有待分配或待处理工单可取消，请刷新后重试");
        }
        return recordWorkflow(actor, author, before, "REPAIR_CANCELLED", reason.strip());
    }
    @Transactional(readOnly = true)
    public Detail detail(AccountPrincipal actor, long id) {
        var order = authorized(actor, id);
        return new Detail(order, repairs.images(id), repairs.logs(id));
    }
    @Transactional(readOnly = true)
    public byte[] image(AccountPrincipal actor, long order, long image) {
        authorized(actor, order);
        var key = repairs.imageKey(order, image);
        if (key == null) throw missing();
        return storage.read(key);
    }
    private Order authorized(AccountPrincipal actor, long id) {
        requireReader(actor);
        var order = repairs.find(id);
        if (order == null || (actor.role() == Role.STUDENT && order.submittedBy() != actor.id())
                || (actor.role() == Role.MAINTAINER && !Objects.equals(order.assigneeId(), actor.id()))) throw missing();
        return order;
    }
    private void requireReader(AccountPrincipal actor) {
        if (!Set.of(Role.STUDENT, Role.DORM_MANAGER, Role.SYSTEM_ADMIN, Role.MAINTAINER).contains(actor.role())) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有访问报修工单的权限");
    }
    private ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "REPAIR_NOT_FOUND", "工单或图片不存在，或无访问权限"); }
    private String fingerprint(Category category, String description, List<RepairImageStorage.Prepared> images) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update((category.name() + "\n" + description + "\n").getBytes(StandardCharsets.UTF_8));
            for (var image : images) digest.update(image.hash().getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
