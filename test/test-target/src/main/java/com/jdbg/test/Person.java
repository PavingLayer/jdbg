package com.jdbg.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sample POJO for testing object inspection.
 * 
 * Tests:
 * - Object reference display
 * - Field inspection
 * - Nested object inspection
 * - Collection inspection
 */
public final class Person {
    
    private final String name;
    private final int age;
    private final Address address;
    private final List<String> hobbies;
    private final Map<String, String> metadata;
    
    public Person(final String name, final int age) {
        this.name = name;
        this.age = age;
        this.address = new Address("123 Main St", "Testville", "12345");
        this.hobbies = new ArrayList<>();
        this.hobbies.add("reading");
        this.hobbies.add("coding");
        this.hobbies.add("debugging");
        this.metadata = new HashMap<>();
        this.metadata.put("role", "developer");
        this.metadata.put("level", "senior");
    }
    
    public String getName() {
        return name;
    }
    
    public int getAge() {
        return age;
    }
    
    public Address getAddress() {
        return address;
    }
    
    public List<String> getHobbies() {
        return hobbies;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    @Override
    public String toString() {
        return "Person{name='" + name + "', age=" + age + "}";
    }
    
    /**
     * Nested class for testing nested object inspection.
     */
    public static final class Address {
        private final String street;
        private final String city;
        private final String zipCode;
        
        public Address(final String street, final String city, final String zipCode) {
            this.street = street;
            this.city = city;
            this.zipCode = zipCode;
        }
        
        public String getStreet() {
            return street;
        }
        
        public String getCity() {
            return city;
        }
        
        public String getZipCode() {
            return zipCode;
        }
        
        @Override
        public String toString() {
            return street + ", " + city + " " + zipCode;
        }
    }
}

