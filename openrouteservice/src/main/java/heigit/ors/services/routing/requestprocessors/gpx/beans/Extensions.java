package heigit.ors.services.routing.requestprocessors.gpx.beans;

import java.math.BigDecimal;
import java.util.Map;

class Extensions {

    private Map<String, Object> keyValueList;


    Extensions() {
        this.keyValueList = null;
    }

    public void addValue(String key, Object value) {
        keyValueList.put(key, value);
    }

//    Extension(String key, Double value) {
//        keyValueList.put(key, value);
//    }
//    Extension(String key, BigDecimal value) {
//        keyValueList.put(key, value);
//    }
//
//    Extension(String key, String value){
//        keyValueList.put(key, value);
//    }

    public Object getValue(String key) {
        return keyValueList.get(key);
    }

}