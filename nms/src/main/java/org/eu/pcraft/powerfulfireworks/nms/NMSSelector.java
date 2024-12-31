package org.eu.pcraft.powerfulfireworks.nms;

import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;

public class NMSSelector {
    public static NMSProvider getImplementation(String version) {
        switch (version) {
            case "1.21.4":
                return new org.eu.pcraft.powerfulfireworks.nms.v1_21_4.NMSProviderImpl();
            default:
                return null;
        }
    }
}
