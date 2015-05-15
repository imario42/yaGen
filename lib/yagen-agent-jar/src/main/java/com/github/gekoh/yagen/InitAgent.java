/*
 * InitAgent
 * Copyright (c) 2012 CREDIT SUISSE Technology and Operations. All Rights Reserved.
 * This software is the proprietary information of CREDIT SUISSE Technology and Operations.
 * Use is subject to license and non-disclosure terms.
 */
package com.github.gekoh.yagen;

import java.lang.instrument.Instrumentation;

/**
 * @author Georg Kohlweiss (F477448)
 */
public class InitAgent {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(InitAgent.class);

    public static void premain(String agentArguments, Instrumentation instrumentation) {
        instrumentation.addTransformer(new PatchTransformer());
    }

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }
}