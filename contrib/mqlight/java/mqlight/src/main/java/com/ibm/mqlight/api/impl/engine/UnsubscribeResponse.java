/*
 *   <copyright 
 *   notice="oco-source" 
 *   pids="5725-P60" 
 *   years="2015" 
 *   crc="1438874957" > 
 *   IBM Confidential 
 *    
 *   OCO Source Materials 
 *    
 *   5724-H72
 *    
 *   (C) Copyright IBM Corp. 2015
 *    
 *   The source code for the program is not published 
 *   or otherwise divested of its trade secrets, 
 *   irrespective of what has been deposited with the 
 *   U.S. Copyright Office. 
 *   </copyright> 
 */

package com.ibm.mqlight.api.impl.engine;

import com.ibm.mqlight.api.impl.Message;

public class UnsubscribeResponse extends Message {
    public final EngineConnection connection;
    public final String topic;
    public final boolean isError;   // TODO: can we do better than just a flag?  Like an error description?
    public UnsubscribeResponse(EngineConnection connection, String topic, boolean isError) {
        this.connection = connection;
        this.topic = topic;
        this.isError = isError;
    }
}
