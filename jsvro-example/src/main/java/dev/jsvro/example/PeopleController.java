package dev.jsvro.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/people")
class PeopleController {

    record Address(String street, String city, String country) {}

    record Person(String name, int age, LocalDate born, Address address, List<String> roles) {}

    @GetMapping
    List<Person> people() {
        return List.of(
                new Person("Alice", 30, LocalDate.of(1996, 3, 12),
                        new Address("Main Street 1", "Oslo", "NO"), List.of("ADMIN", "USER")),
                new Person("Bob", 40, LocalDate.of(1986, 8, 1),
                        new Address("Parkveien 4", "Bergen", "NO"), List.of("USER")));
    }
}
