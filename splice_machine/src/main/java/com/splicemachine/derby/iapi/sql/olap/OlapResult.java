package com.splicemachine.derby.iapi.sql.olap;

import java.io.Serializable;

/**
 * Created by dgomezferro on 3/15/16.
 */
public interface OlapResult extends Serializable {
    short getCallerId();
    void setCallerId(short callerId);
    Throwable getThrowable();
}