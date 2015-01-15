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

import com.ibm.mqlight.api.endpoint.Endpoint;
import com.ibm.mqlight.api.impl.Message;

public class OpenRequest extends Message {

    public final Endpoint endpoint;
    public final String clientId;
    
    public OpenRequest(Endpoint endpoint, String clientId) {
        this.endpoint = endpoint;
        this.clientId = clientId;
    }
}
