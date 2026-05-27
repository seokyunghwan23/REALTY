package com.realty.Realtymate.types;

import java.util.HashMap;
import java.util.Map;

public class WOORI_USERUID_DICT {
    private static final Map<String, String> dict = new HashMap<>();

    static {
        dict.put("11620-2022-00158", "40618");
        dict.put("11560-2024-00003", "40286");
        dict.put("11710-2022-00018", "40097");
        dict.put("11620-2024-00030", "40963");
        dict.put("11560-2016-00039", "40228");
        dict.put("11620-2020-00017", "40268");
        dict.put("11440-2021-00132", "40075");
        dict.put("11440-2023-00056", "38919");
    }

    public static String get(String establishRegistrationNo) {
        return dict.get(establishRegistrationNo);
    }

    public static boolean contains(String establishRegistrationNo) {
        return dict.containsKey(establishRegistrationNo);
    }
}
