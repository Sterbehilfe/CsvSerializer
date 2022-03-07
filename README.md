# CsvSerializer

A Java CSV serializer and deserializer that can work with the following data types:

`
String, char, byte, short, int, long, boolean, float, double
`

## Usage

### Example data class

An example data class with the fields that will deserialized and serialized marked by the annotation `@CsvField`. The class is required to have a constructor that has no parameters and each field
marked by the annotation needs a getter and setter, which name comes from the field's name with the first char in upper case prefixed by "get" or "set".

```java
import csvserializer.annotations.CsvField;

public class Data {

    @CsvField
    private String name;

    @CsvField
    private int number;

    public Data() {
    }

    public Data(String name, int number) {
        this.name = name;
        this.number = number;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumber() {
        return this.number;
    }

    public void setNumber(int number) {
        this.number = number;
    }
}
```

### Usage of the `CsvSerializer`

The `CsvSerializer` needs the item class as a generic parameter and the class of the item passed to the constructor. You can now add items of the specified type to the `CsvSerializer` by using the
available methods of adding. The items can now be serialized by calling `serialize()`, which returns a `String` in CSV format, or `serializeToFile(String)`, which takes in the path to a file and
writes the CSV formatted `String` to the file. To deserialize, you can call `deserialize(String)`, which takes in a `String` in CSV format, or call `deserializeFromFile(String)`, which takes in a path
to a CSV file and reads the content. After deserializing you can get an array of all items by calling `getItems()`.

```java
import csvserializer;

import java.util.ArrayList;

public static class Tester {

    public static void main(String[] args) {

        CsvSerializer<Data> csvSerializer = new CsvSerializer<>(Data.class);

        Data d1 = new Data("Test1", 123);
        Data d2 = new Data("Test2", 456);
        Data d3 = new Data("Test3", 789);
        csvSerializer.addItems(d1, d2, d3);

        String ser = csvSerializer.serialize();
        csvSerializer.serializeToFile("C:\\Documents\\Data.csv");

        csvSerializer.deserialize(ser);
        csvSerializer.deserializeFromFile("C:\\Documents\\Data.csv");

        ArrayList<Data> data = csvSerializer.getItems();
    }
}
```
### CSV output

This is what the serialized data would look like in CSV format.

```
"number","name",
"123","Test1",
"456","Test2",
"789","Test3",
```
