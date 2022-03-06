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
import java.util.function.Function;

/**
 * A CSV serializer and deserializer to read and write items of a specified type.
 *
 * @param <T> The specified item type
 * @author Hendrik
 * @version 1.0 - 02 Mar 2022
 */
public class CsvSerializer<T> {

    /**
     * The list the CSV items are stored in.
     */
    private final ArrayList<T> elements;

    /**
     * The class type of the item type which is stored to use for reflection.
     */
    private final Class<T> contentClass;

    /**
     * A map of the field that have been marked with the annotation {@link csvserializer.annotations.CsvField} in the
     * item class.
     */
    private final HashMap<String, CsvFieldData> csvFields;

    /**
     * The annotation {@link csvserializer.annotations.CsvField} the fields of the item class have to be marked with to
     * be serialized or deserialized.
     */
    private final Class<CsvField> ANNOTATION_CLASS = CsvField.class;

    /**
     * The separator that is placed between two values in the CSV file.
     */
    private final char CSV_SEPARATOR = ',';

    /**
     * The separator that is used to begin a new line in the CSV file.
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
     *                                     has been marked to with the annotation {@link csvserializer.annotations.CsvField}
     * @throws NoFieldMarkedException      will be thrown if now field has been marked with the annotation
     *                                     {@link csvserializer.annotations.CsvField}
     */
    public CsvSerializer(Class<T> contentClass) throws UnserializableTypeException, NoFieldMarkedException {
        this.elements = new ArrayList<>();
        this.contentClass = contentClass;
        this.csvFields = getCsvFields();

        checkForIllegalType();
        checkIfAnyFieldsAreMarked();
        ensureConstructorExistence();
    }

    /**
     * Gets the items currently stored that wait to be serialized or have been deserialized.
     *
     * @return a clone of the {@link java.util.ArrayList} in which the items are stored
     */
    public ArrayList<T> getItems() {
        return (ArrayList<T>) this.elements.clone();
    }

    /**
     * Gets information about the marked field of the item class. Saves the getter and setter for each field in a
     * {@link java.util.HashMap} to use them for getting the values when serializing and setting the value when
     * deserializing. The key is of the type {@link java.lang.String} which is the name of the field and the value is
     * the information stored in the type {@link csvserializer.CsvFieldData}.
     *
     * @return a HashMap with the information about the fields.
     */
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
            CsvFieldData fieldData = new CsvFieldData(getter, setter, f.getType());
            result.put(f.getName(), fieldData);
        }
        return result;
    }

    /**
     * Gets the amount of items currently stored in this instance.
     *
     * @return the amount of items
     */
    public int getItemCount() {
        return this.elements.size();
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

    /**
     * Removes the items from the list for which the {@link java.util.function.Function} condition returns {@code true}.
     *
     * @param condition the condition, if returns true the element will be removed from the list.
     * @return the amount of items removed from the list
     */
    public int removeItem(Function<T, Boolean> condition) {
        int count = 0;
        for (T t : this.elements) {
            if (condition.apply(t)) {
                this.elements.remove(t);
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the header for the CSV file by appending all names of the fields.
     *
     * @return the CSV file header
     */
    private String getCsvHeader() {
        StringBuilder builder = new StringBuilder();
        this.csvFields.keySet().forEach(fName -> builder.append('"').append(fName).append('"').append(CSV_SEPARATOR));
        return builder.append(LINE_SEPARATOR).toString();
    }

    /**
     * Creates a new instance of the item class.
     *
     * @return a new instance of the item class
     */
    private T getNewInstance() {
        try {
            return (T) this.contentClass.getConstructor().newInstance();
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException ex) {
            printEx(ex);
        }
        return null;
    }

    /**
     * Deserializes items of the provided type from a CSV file.
     *
     * @param path the path to a file containing items of the provided type in a CSV format
     * @throws IOException throws if there is an error with the provided path
     */
    public void deserializeFromFile(String path) throws IOException {
        String csvString = Files.readString(Path.of(path));
        deserialize(csvString);
    }

    /**
     * Deserializes items of the provided type from a CSV string.
     *
     * @param csvString the CSV string
     */
    public void deserialize(String csvString) {
        this.elements.clear();

        String[] split = csvString.replaceAll("\r", "").split("\n");
        ArrayList<String> csvLines = arrayToList(split);
        String[] headerFields = removeQuotes(csvLines.get(0).split("" + CSV_SEPARATOR));
        csvLines.remove(0);

        for (String ln : csvLines) {
            String[] values = removeQuotes(ln.split("" + CSV_SEPARATOR));

            T item = getNewInstance();
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

            this.elements.add(item);
        }
    }

    /**
     * Serializes the containing item of provided type.
     *
     * @return a string in CSV format of the containing items
     */
    public String serialize() {
        StringBuilder builder = new StringBuilder(getCsvHeader());
        for (T item : this.elements) {
            for (String fName : this.csvFields.keySet()) {
                try {
                    String value = this.csvFields.get(fName).getGetter().invoke(item).toString();
                    builder.append('"').append(value).append('"').append(CSV_SEPARATOR);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    printEx(ex);
                }
            }
            builder.append(LINE_SEPARATOR);
        }
        return builder.toString();
    }

    /**
     * Serializes the containing items and writes the CSV string to the provided file.
     *
     * @param path the path of the file the CSV string will be written to
     * @throws IOException throws if there is an error with the provided path
     */
    public void serializeToFile(String path) throws IOException {
        String content = serialize();
        Files.writeString(Path.of(path), content);
    }

    /**
     * Checks if any fields that are marked with the annotation {@link csvserializer.annotations.CsvField} have an
     * unserializable or undeserializable value type. Throws an exception for the first type it finds.
     *
     * @throws UnserializableTypeException throws if there is an unserializable or deserializable type marked
     */
    private void checkForIllegalType() throws UnserializableTypeException {
        for (String fName : this.csvFields.keySet()) {
            boolean isIllegal = true;
            CsvFieldData field = this.csvFields.get(fName);
            for (Class<?> c : AVAILABLE_TYPES) {
                if (field.getType() == c) {
                    isIllegal = false;
                }
            }

            if (isIllegal) {
                throw new UnserializableTypeException("The field " + fName + " of type " + field.getType().getName() + " is an unserializable type.");
            }
        }
    }

    /**
     * Checks if at least one field is marked by the annotation {@link csvserializer.annotations.CsvField} to be
     * serialized or deserialized. If not, throws an exception.
     *
     * @throws NoFieldMarkedException throws if no fields are marked
     */
    private void checkIfAnyFieldsAreMarked() throws NoFieldMarkedException {
        if (this.csvFields.isEmpty()) {
            throw new NoFieldMarkedException("No field of the class " + contentClass.getName() + " has been marked with the " + ANNOTATION_CLASS.getName() + " annotation to be serialized.");
        }
    }

    /**
     * Check if the item class has a constructor that has no parameters. If not, throws an exception.
     *
     * @throws NoSuchMethodError throws if the item class doesn't have a constructor with no parameters
     */
    private void ensureConstructorExistence() throws NoSuchMethodError {
        try {
            contentClass.getConstructor();
        } catch (NoSuchMethodException ex) {
            throw new NoSuchMethodError("The class " + contentClass.getName() + " needs a constructor that takes no parameters.");
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

    /**
     * Return a string in which the first char has been made upper case.
     *
     * @param input the string that will be modified
     * @return the string with the first char in upper case
     */
    private String firstCharToUpper(String input) {
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    /**
     * Removes all quotes (") of all strings in the array.
     *
     * @param input the array of strings the quotes will be removed in
     * @return a string array without quotes
     */
    private String[] removeQuotes(String[] input) {
        for (int i = 0; i < input.length; i++) {
            input[i] = input[i].replaceAll("\"", "");
        }
        return input;
    }

    /**
     * An easier way to convert an array to an {@link java.util.ArrayList}.
     *
     * @param items the array that will be converted
     * @param <E>   the type of the array content
     * @return the {@link java.util.ArrayList} containing all array elements
     */
    private <E> ArrayList<E> arrayToList(E[] items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    /**
     * Prints an exception to the error output.
     *
     * @param ex the exception that will be printed
     */
    private void printEx(Exception ex) {
        StringBuilder builder = new StringBuilder("Exception logger: ");
        builder.append("\n\t").append(ex.getClass().getName()).append(":\n\t").append(ex.getMessage());
        for (StackTraceElement stkTrc : ex.getStackTrace()) {
            builder.append("\n\t\t").append(stkTrc.toString());
        }
        System.err.println(builder);
    }
}
