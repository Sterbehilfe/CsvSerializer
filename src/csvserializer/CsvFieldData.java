package csvserializer;

import java.lang.reflect.Method;

public class CsvFieldData {

    private final Method getter;

    private final Method setter;

    private final Class<?> type;

    public CsvFieldData(Method getter, Method setter, Class<?> type) {
        this.getter = getter;
        this.setter = setter;
        this.type = type;
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
