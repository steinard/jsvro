package dev.jsvro.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PeopleControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void servesPeopleAsJsvro() throws Exception {
        mvc.perform(get("/people").accept("application/vnd.jsvro"))
                .andExpect(status().isOk())
                .andExpect(content().string("""
                        {"jsvro":"1","columns":[{"name":"name","type":"string"},{"name":"age","type":"integer"},\
                        {"name":"born","type":"date"},{"name":"address","type":"object","columns":[\
                        {"name":"street","type":"string"},{"name":"city","type":"string"},{"name":"country","type":"string"}]},\
                        {"name":"roles","type":"array","items":{"type":"string"}}]}
                        ["Alice",30,"1996-03-12",["Main Street 1","Oslo","NO"],["ADMIN","USER"]]
                        ["Bob",40,"1986-08-01",["Parkveien 4","Bergen","NO"],["USER"]]
                        """));
    }
}
