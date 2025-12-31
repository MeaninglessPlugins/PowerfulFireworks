package org.eu.pcraft.powerfulfireworks.nms;

import org.eu.pcraft.powerfulfireworks.nms.common.NMSProvider;
import org.eu.pcraft.powerfulfireworks.nms.v1_21_4.NMSProviderImpl;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Map.entry;

public class NMSSelector {
    static List<Map.Entry<String, Supplier<NMSProvider>>> versionList = List.of(
            entry("1.20.5", NMSProviderImpl::new),
            entry("1.20.4", org.eu.pcraft.powerfulfireworks.nms.v1_20_4.NMSProviderImpl::new),
            entry("1.20.2", org.eu.pcraft.powerfulfireworks.nms.v1_20_2.NMSProviderImpl::new),
            entry("1.19.4", org.eu.pcraft.powerfulfireworks.nms.v1_19_4.NMSProviderImpl::new),
            entry("1.18.2", org.eu.pcraft.powerfulfireworks.nms.v1_18_2.NMSProviderImpl::new)
    );
    private static List<Integer> parseVersion(String version) {
        List<Integer> parts = new ArrayList<>();
        Scanner scanner = new Scanner(version);
        try(scanner){
            scanner.useDelimiter("\\.");
            while (scanner.hasNextInt()) {
                parts.add(scanner.nextInt());
            }
        }
        return parts;
    }

    // 如果L1版本大于等于L2，返回true，否则返回false
    private static boolean compareVersion(List<Integer> l1,List<Integer> l2){
        int length = Math.min(l1.size(), l2.size());
        for(int i=0;i<length;i++){
            if(l1.get(i).equals(l2.get(i)))continue;
            return l1.get(i)>l2.get(i);
        }
        return l1.size()>=l2.size();
    }

    public static NMSProvider getImplementation(String version) {
        for(Map.Entry<String, Supplier<NMSProvider>> entry:versionList){
            if(compareVersion(parseVersion(version),parseVersion(entry.getKey()))){
                return entry.getValue().get();
            }
        }
        return null;
    }
}
