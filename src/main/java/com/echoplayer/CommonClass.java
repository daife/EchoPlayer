package com.echoplayer;

import com.echoplayer.Constants;
import com.echoplayer.platform.Services;
public class CommonClass {
    public static void init() {
        Constants.LOG.info("{} initialized on {} ({})", Constants.MOD_NAME, Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
    }
}
