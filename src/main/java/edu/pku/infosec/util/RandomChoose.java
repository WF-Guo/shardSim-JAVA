package edu.pku.infosec.util;

import com.alibaba.fastjson.JSONObject;

import java.util.Random;

public class RandomChoose {
    private static final Random random = new Random();

    public static int chooseByDistribution(JSONObject distribution) {
        double v = random.nextDouble();
        for (String s : distribution.keySet()) {
            v -= Double.parseDouble(s);
            if (v <= 0)
                return distribution.getInteger(s);
        }
        return 0; // not gonna happen
    }

}
