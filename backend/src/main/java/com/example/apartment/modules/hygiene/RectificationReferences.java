package com.example.apartment.modules.hygiene;

import com.example.apartment.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Deactivation shares the formal-score business lock; closed history never blocks deactivation. */
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class RectificationReferences {
    private final RectificationMapper events;
    public RectificationReferences(RectificationMapper events) { this.events=events; }
    public void requireBuildingFinished(long id) { requireFinished(events.unfinished(id,null)); }
    public void requireRoomFinished(long id) { requireFinished(events.unfinished(null,id)); }
    private void requireFinished(long count) {
        if(count>0) throw new ApiException(HttpStatus.CONFLICT,"UNFINISHED_RECTIFICATION","存在未关闭整改，请核实并关闭后再停用");
    }
}
