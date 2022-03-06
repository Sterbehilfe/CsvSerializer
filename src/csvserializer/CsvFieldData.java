package csvserializer;

import java.lang.reflect.Method;

/**
 * A record in which the type, getter and setter for each field are stored in.
 */
record CsvFieldData(Method getter, Method setter, Class<?> type) {

    /**
     * Gets the field's getter.
     *
     * @return the field's getter
     */
    public Method getGetter() {
        return getter;
    }

    /**
     * Gets the field's setter.
     *
     * @return the field's setter.
     */
    public Method getSetter() {
        return setter;
    }

    /**
     * Gets the field's type.
     *
     * @return the field's type
     */
    public Class<?> getType() {
        return type;
    }
}
