package org.eu.pcraft.powerfulfireworks.utils;

import lombok.AllArgsConstructor;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.LinkedHashMap;

@ConfigSerializable
@AllArgsConstructor
public class Pair<T>{
    public Pair(){}
    public Pair(LinkedHashMap<String, T> map){
        minimum = map.get("minimum");
        maximum = map.get("maximum");
    }
    public T minimum;
    public T maximum;
}