package org.eu.pcraft.powerfulfireworks.utils;

import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class Interval<T>{
    public Interval(){}
    public Interval(Object obj){
        if(obj instanceof Map){
            Map<String, T> map = (Map<String, T>) obj;
            minimum = map.get("minimum");
            maximum = map.get("maximum");
            return;
        }
        if(obj instanceof List){
            List<T> list = (List<T>) obj;
            minimum = list.get(0);
            maximum = list.get(1);
            return;
        }
        maximum = minimum = (T) obj;
    }
    public Interval(Interval<T> interval){
        this.maximum=interval.maximum;
        this.minimum=interval.minimum;
    }
    public T minimum;
    public T maximum;
    public String toString(){
        return "["+minimum+","+maximum+"]";
    }
}