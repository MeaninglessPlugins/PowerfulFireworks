package org.eu.pcraft.powerfulfireworks.nms;

import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;

public class NMSSelector {
    public static NMSProvider getImplementation(String version) {
        return switch (version) {
            case "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21", "1.20.6", "1.20.5" ->
                    new org.eu.pcraft.powerfulfireworks.nms.v1_21_4.NMSProviderImpl();
            case "1.20.4" -> new org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSProviderImpl();
            case "1.20.2" -> new org.eu.pcraft.powerfulfireworks.nms.v1_20_2.NMSProviderImpl();
            case "1.19.4" -> new org.eu.pcraft.powerfulfireworks.nms.v1_19_4.NMSProviderImpl();
            default -> null;
        };
    }
}
