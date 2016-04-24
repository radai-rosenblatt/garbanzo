# Garbanzo - A Pojo to INI serialization library 
### Briefly
Turns this:
```java
public class Person {
    private String name;
    private Date birthday;
}
```
Into this:
```ini
name = Elvis
birthday = 08/01/1935
```
and back. Like so:
```java
Person p = ...;
String marshalled = Garbanzo.marshal(p);
Person unmarshalled = Garbanzo.unmarshall(Person.class, marshalled); 
```
### Get Some
```xml
<dependency>
    <groupId>com.github.radai-rosenblatt</groupId>
    <artifactId>garbanzo</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
...
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```
### Feature Highlights
Collections, arrays and maps:
```java
public class Whatever {
    private List<String> list;
    private int[] array;
    private Map<UUID, SomeEnum> map;
}
```
```ini
array = 1
array = 2
array = 3
list = string1
list = string2

[map]
f03bca19-139f-428b-b9dc-0a398fa5c745 = VAL2
67662852-456a-4c0f-b51d-a0494ef76973 = VAL1
```
Section classes:
```java
public class Outer {
    private String outerProp;
    private Inner inner;
}
public class Inner {
    private String innerProp;
}
```
```ini
outerProp = such value

[inner]
innerProp = much wow
```
Comments:
```java
@IniComment("Abandon all hope, ye who enter here")
public class Configuration {
    @IniComment("cant touch this")
    private String prop;
}
```
```ini
#Abandon all hope, ye who enter here
#cant touch this
prop = sensitive
```
