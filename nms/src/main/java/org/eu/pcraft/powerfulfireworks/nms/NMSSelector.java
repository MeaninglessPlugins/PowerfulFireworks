package org.eu.pcraft.powerfulfireworks.nms;

import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;

public class NMSSelector {
    public static NMSProvider getImplementation(String version) {
        switch (version) {
            case "1.21.4":
            case "1.21.3":
            case "1.21.2":
            case "1.21.1":
            case "1.21":
            case "1.20.6":
            case "1.20.5":
                return new org.eu.pcraft.powerfulfireworks.nms.v1_21_4.NMSProviderImpl();
            case "1.20.4":
                return new org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSProviderImpl();
            default:
                return null;
        }
    }
}
