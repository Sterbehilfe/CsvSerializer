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

/**
 * A CSV serializer and deserializer to read and write items of a specified type.
 *
 * @author Hendrik
 * @param <T> The specified item type
 */
public class CsvSerializer<T> {

    /**
     * The list the CSV items are stored in.
     */
    private final ArrayList<T> elements;

    /**
     * The class type of the item type which is stored to use for reflection.
     */
    private final Class<?> contentClass;

    /**
     * A map of the field that have been marked with the annotation {@link csvserializer.annotations.CsvField} in the
     * item class.
     */
    private final HashMap<String, CsvFieldData> csvFields;

    /**
     * The annotation {@link csvserializer.annotations.CsvField} the fields of the item class have to be marked with to
     * be serialized or deserialized.
     */
    private final Class ANNOTATION_CLASS = CsvField.class;

    /**
     * The separator that is placed between two values in the CSV file.
     */
    private final char CSV_SEPARATOR = ',';

    /**
     * The separator that is is used to begin a new line in the CSV file.
     */
    private final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * An array of types that can be serialized or deserialized with this class.
     */
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

    /**
     * The basic constructor for the CsvSerializer that takes in the type of the serialized and deserialized items.
     *
     * @param contentClass the type of the serialized and deserialized items
     * @throws UnserializableTypeException will be thrown if any field with an unserializable or undeserializable type
     * has been marked to with the annotation {@link csvserializer.annotations.CsvField}
     * @throws NoFieldMarkedException will be thrown if now field has been marked with the annotation
     * {@link csvserializer.annotations.CsvField}
     */
    public CsvSerializer(Class<?> contentClass) throws UnserializableTypeException, NoFieldMarkedException {
        this.elements = new ArrayList<>();
        this.contentClass = contentClass;
        this.csvFields = getCsvFields();

        checkForIllegalType();
        checkIfAnyFieldsAreMarked();
        ensureConstructorExistance();
    }

    /**
     * Gets the items currently stored that wait to be serialized or have been deserialized.
     *
     * @return a clone of the {@link java.util.ArrayList} in which the items are stored
     */
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

    /**
     * Adds a single item to the list.
     *
     * @param item the item that will be added
     */
    public void addItem(T item) {
        if (item != null) {
            this.elements.add(item);
        }
    }

    /**
     * Adds a collection of items to the list.
     *
     * @param items the items that will be added
     */
    public void addRange(Iterable<T> items) {
        items.forEach(i -> {
            if (i != null) {
                this.elements.add(i);
            }
        });
    }

    /**
     * Adds an array of items to the list.
     *
     * @param items the items that will be added
     */
    public void addRange(T[] items) {
        for (T i : items) {
            if (i != null) {
                this.elements.add(i);
            }
        }
    }

    /**
     * Removes an item by index from the list.
     *
     * @param idx the index of the item that will be removed
     */
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
            builder.append('"').append(fName).append('"').append(CSV_SEPARATOR);
        });
        return builder.append(LINE_SEPARATOR).toString();
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

    public void deserializeFromFile(String path) throws IOException {
        String csvString = Files.readString(Path.of(path));
        deserialize(csvString);
    }

    public void deserialize(String csvString) throws IOException {
        this.elements.clear();

        String[] split = csvString.replaceAll("\r", "").split("\n");
        ArrayList<String> csvLines = arrayToList(split);
        String[] headerFields = removeQuotes(csvLines.get(0).split("" + CSV_SEPARATOR));
        csvLines.remove(0);

        for (String ln : csvLines) {
            String[] values = removeQuotes(ln.split("" + CSV_SEPARATOR));

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
                    builder.append('"').append(value).append('"').append(CSV_SEPARATOR);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    printEx(ex);
                }
            }
            builder.append(LINE_SEPARATOR);
        }
        return builder.toString();
    }

    public void serializeToFile(String path) throws IOException {
        String content = serialize();
        Files.writeString(Path.of(path), content);
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
                    + " with the " + ANNOTATION_CLASS.getName() + " annotation to be serialized.");
        }
    }

    private void ensureConstructorExistance() throws NoSuchMethodError {
        try {
            contentClass.getConstructor();
        } catch (NoSuchMethodException ex) {
            throw new NoSuchMethodError("The class " + contentClass.getName() + " needs a constructor that takes no"
                    + " parameters.");
        }
    }

    private Object convertValue(String value, Class<?> type) {
        if (type == String.class) {
            return value;
        } else if (type == Character.class || type == char.class) {
            if (value.length() > 0) {
                return value.charAt(0);
            } else {
                return '\0';
            }
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

    private <E> ArrayList<E> arrayToList(E[] items) {
        return new ArrayList<>(Arrays.asList(items));
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
