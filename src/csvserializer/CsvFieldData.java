package csvserializer;

import java.lang.reflect.Method;

public class CsvFieldData {

    private final String name;

    private final Method getter;

    private final Method setter;

    private final Class<?> type;

    public CsvFieldData(String name, Method getter, Method setter, Class<?> type) {
        this.name = name;
        this.getter = getter;
        this.setter = setter;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Method getGetter() {
        return getter;
    }

    public Method getSetter() {
        return setter;
    }

    public Class<?> getType() {
        return type;
    }
}
