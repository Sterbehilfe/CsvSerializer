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
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class CsvSerializer<T> {

    private final ArrayList<T> elements;

    private final Class<?> contentClass;

    private final HashMap<String, CsvFieldData> csvFields;

    private final Class ANNOTATION_CLASS = CsvField.class;

    private final char CSV_SEPERATOR = ',';

    private final String LINE_SEPERATOR = System.lineSeparator();

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
        this.contentClass = itemClass;
        this.csvFields = getCsvFields();

        checkForIllegalType();
        checkIfAnyFieldsAreMarked();
    }

    public ArrayList<T> getItems() {
        return (ArrayList<T>) this.elements.clone();
    }

    private HashMap<String, CsvFieldData> getCsvFields() {
        HashMap<String, CsvFieldData> result = new HashMap<>();
        Field[] fields = this.contentClass.getDeclaredFields();
        Method[] methods = this.contentClass.getDeclaredMethods();

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
            result.put(f.getName(), new CsvFieldData(getter, setter, f.getType()));
        }
        return result;
    }

    public void addItem(T item) {
        if (item != null) {
            this.elements.add(item);
        }
    }

    public void addRange(Iterable<T> items) {
        items.forEach(i -> {
            if (i != null) {
                this.elements.add(i);
            }
        });
    }

    public void addRange(T[] items) {
        for (T i : items) {
            if (i != null) {
                this.elements.add(i);
            }
        }
    }

    public void removeItem(int idx) {
        this.elements.remove(idx);
    }

    public int removeItem(Function<T, Boolean> condition) {
        int removeCount = 0;
        for (T t : this.elements) {
            if (condition.apply(t)) {
                this.elements.remove(t);
                removeCount++;
            }
        }
        return removeCount;
    }

    private String getCsvHeader() {
        StringBuilder builder = new StringBuilder();
        this.csvFields.keySet().forEach(fName -> {
            builder.append('"').append(fName).append('"').append(CSV_SEPERATOR);
        });
        return builder.append(LINE_SEPERATOR).toString();
    }

    private Object getNewInstance() {
        try {
            return this.contentClass.getConstructor().newInstance();
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException
                | SecurityException | InvocationTargetException ex) {
            printEx(ex);
        }
        return null;
    }

    public void deserializeFromFile(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        deserialize(lines);
    }

    public void deserialize(List<String> csvLines) throws IOException {
        this.elements.clear();

        String[] headerFields = removeQuotes(csvLines.get(0).split("" + CSV_SEPERATOR));
        csvLines.remove(0);

        for (String ln : csvLines) {
            String[] values = removeQuotes(ln.split("" + CSV_SEPERATOR));

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
            builder.append(LINE_SEPERATOR);
        }
        return builder.toString();
    }

    public void serializeToFile(Path path) throws IOException {
        String content = serialize();
        Files.writeString(path, content);
    }

    private void checkForIllegalType() throws UnserializableTypeException {
        for (String n : this.csvFields.keySet()) {
            boolean isIllegal = true;
            CsvFieldData field = this.csvFields.get(n);
            for (Class<?> c : AVAILABLE_TYPES) {
                if (field.getType() == c) {
                    isIllegal = false;
                }
            }

            if (isIllegal) {
                throw new UnserializableTypeException("The field " + n + " of type " + field.getType().getName()
                        + " is an unserializable type.");
            }
        }
    }

    private void checkIfAnyFieldsAreMarked() throws NoFieldMarkedException {
        if (this.csvFields.isEmpty()) {
            throw new NoFieldMarkedException("No field of the class " + contentClass.getName() + " has been marked"
                    + "with the " + ANNOTATION_CLASS.getName() + " annotation to be serialized.");
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
        builder.append("\n\t").append(ex.getClass().getName()).append(":\n\t");
        builder.append(ex.getMessage());
        for (var stkTrc : ex.getStackTrace()) {
            builder.append("\n\t\t").append(stkTrc.toString());
        }
        System.err.println(builder.toString());
    }
}
