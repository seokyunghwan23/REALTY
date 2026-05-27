package com.realty.Realtymate.utils;

import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class Utils {
    public static String mapToUrlParams(Map<String, Object> params) {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public static ArrayList<String> checkNewAd(ArrayList<String> previousList, ArrayList<String> currentList) {
        ArrayList newAdList = new ArrayList();
        currentList.forEach(item -> {
            if (!previousList.contains(item)) {
                newAdList.add(item);
            }
        });
        return newAdList;
    }

}
