package csvserializer;

import csvserializer.annotations.CsvField;
import csvserializer.exceptions.NoFieldMarkedException;
import csvserializer.exceptions.UnserializableTypeException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class CsvSerializer<T> {

    private final ArrayList<T> elements;

    private final Class ANNOTATION_CLASS = CsvField.class;

    private final Class<?> itemClass;

    private final HashMap<String, CsvFieldData> csvFields;

    private final char CSV_SEPERATOR = ',';

    private final char NEW_LINE_CHAR = '\n';

    private final Class<?>[] AVAILABLE_TYPES = {
        String.class,
        Character.class,
        char.class,
        Byte.class,
        byte.class,
        Short.class,
        short.class,
        Integer.class,
        int.class,
        Long.class,
        long.class,
        Boolean.class,
        boolean.class,
        Float.class,
        float.class,
        Double.class,
        double.class
    };

    public CsvSerializer(Class<?> itemClass) throws UnserializableTypeException, NoFieldMarkedException {
        this.elements = new ArrayList<>();
        this.itemClass = itemClass;
        this.csvFields = getCsvFields();

        checkForIllegalType();
        checkIfAnyFieldsAreMarked();
    }

    public ArrayList<T> getItems() {
        return this.elements;
    }

    private HashMap<String, CsvFieldData> getCsvFields() {
        HashMap<String, CsvFieldData> result = new HashMap<>();
        Field[] fields = this.itemClass.getDeclaredFields();
        Method[] methods = this.itemClass.getDeclaredMethods();

        for (Field f : fields) {
            if (f.getDeclaredAnnotation(ANNOTATION_CLASS) == null) {
                continue;
            }

            Method getter = null;
            Method setter = null;
            for (Method m : methods) {
                if (m.getName().equals("get" + firstCharToUpper(f.getName()))) {
                    getter = m;
                }

                if (m.getName().equals("set" + firstCharToUpper(f.getName()))) {
                    setter = m;
                }
            }

            if (getter == null || setter == null) {
                continue;
            }
            result.put(f.getName(), new CsvFieldData(f.getName(), getter, setter, f.getType()));
        }
        return result;
    }

    public void appendItem(T item) {
        if (item != null) {
            this.elements.add(item);
        }
    }

    public void appendRange(Collection<T> items) {
        items.forEach(i -> {
            if (i != null) {
                this.elements.add(i);
            }
        });
    }

    public void appendRange(T[] items) {
        for (T i : items) {
            if (i != null) {
                this.elements.add(i);
            }
        }
    }

    private String getCsvHeader() {
        StringBuilder builder = new StringBuilder();
        for (String fName : this.csvFields.keySet()) {
            builder.append('"').append(fName).append('"').append(CSV_SEPERATOR);
        }
        return builder.append(NEW_LINE_CHAR).toString();
    }

    private Object getNewInstance() {
        try {
            return this.itemClass.getConstructor().newInstance();
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException
                | SecurityException | InvocationTargetException ex) {
            printEx(ex);
        }
        return null;
    }

    public void deserialize(String path) throws IOException {
        this.elements.clear();

        List<String> lines = Files.readAllLines(Path.of(path));

        String[] headerFields = removeQuotes(lines.get(0).split("" + CSV_SEPERATOR));
        lines.remove(0);

        for (String l : lines) {
            String[] values = removeQuotes(l.split("" + CSV_SEPERATOR));

            Object item = getNewInstance();
            if (item == null) {
                continue;
            }
            for (String fName : this.csvFields.keySet()) {
                try {
                    int idx = Arrays.asList(headerFields).indexOf(fName);
                    CsvFieldData field = this.csvFields.get(fName);
                    Object value = convertValue(values[idx], field.getType());
                    field.getSetter().invoke(item, value);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    printEx(ex);
                }
            }

            this.elements.add((T) item);
        }
    }

    public String serialize() {
        StringBuilder builder = new StringBuilder(getCsvHeader());
        for (T item : this.elements) {
            for (String fName : this.csvFields.keySet()) {
                try {
                    String value = this.csvFields.get(fName).getGetter().invoke((Object) item).toString();
                    builder.append('"').append(value).append('"').append(CSV_SEPERATOR);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    printEx(ex);
                }
            }
            builder.append(NEW_LINE_CHAR);
        }
        return builder.toString();
    }

    public void serializeToFile(String path) throws IOException {
        String content = serialize();
        Files.writeString(Path.of(path), content);
    }

    private void checkForIllegalType() throws UnserializableTypeException {
        for (CsvFieldData d : this.csvFields.values()) {
            boolean isIllegal = true;
            for (Class<?> c : AVAILABLE_TYPES) {
                if (d.getType() == c) {
                    isIllegal = false;
                }
            }

            if (isIllegal) {
                throw new UnserializableTypeException("The field " + d.getName() + " of type " + d.getType().getName()
                        + " is an unserializable type.");
            }
        }
    }

    private void checkIfAnyFieldsAreMarked() throws NoFieldMarkedException {
        if (this.csvFields.isEmpty()) {
            throw new NoFieldMarkedException("");
        }
    }

    private Object convertValue(String value, Class<?> type) {
        if (type == String.class) {
            return value;
        } else if (type == Character.class || type == char.class) {
            return value.charAt(0);
        } else if (type == Byte.class || type == byte.class) {
            return Byte.parseByte(value);
        } else if (type == Short.class || type == short.class) {
            return Short.parseShort(value);
        } else if (type == Integer.class || type == int.class) {
            return Integer.parseInt(value);
        } else if (type == Long.class || type == long.class) {
            return Long.parseLong(value);
        } else if (type == Boolean.class || type == boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (type == Float.class || type == float.class) {
            return Float.parseFloat(value);
        } else if (type == Double.class || type == double.class) {
            return Double.parseDouble(value);
        } else {
            return null;
        }
    }

    private String firstCharToUpper(String input) {
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    private String[] removeQuotes(String[] input) {
        for (int i = 0; i < input.length; i++) {
            input[i] = input[i].replaceAll("\"", "");
        }
        return input;
    }

    private void printEx(Exception ex) {
        StringBuilder builder = new StringBuilder("Exception logger: ");
        builder.append("\n\t").append(ex.getClass().getName())
                .append(":\n\t");
        builder.append(ex.getMessage());
        for (var stkTrc : ex.getStackTrace()) {
            builder.append("\n\t\t").append(stkTrc.toString());
        }
        System.err.println(builder.toString());
    }
}
