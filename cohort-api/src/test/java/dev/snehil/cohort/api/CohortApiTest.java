package dev.snehil.cohort.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cohort.seed-defaults=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CohortApiTest {

    @Autowired
    MockMvc mvc;

    private void createCohort(String json) throws Exception {
        mvc.perform(post("/cohorts").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void createListUpdateAndDeleteACohort() throws Exception {
        mvc.perform(post("/cohorts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Weekly buyer going quiet", "priority": 50,
                         "rule": "orders_30d >= 4 and days_since_last_order > 14"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/cohorts/weekly-buyer-going-quiet"))
                .andExpect(jsonPath("$.id").value("weekly-buyer-going-quiet"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.canonicalRule").value("orders_30d >= 4 AND days_since_last_order > 14"));

        mvc.perform(post("/cohorts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Weekly buyer going quiet", "priority": 60, "rule": "orders_30d >= 5"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/cohorts")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/cohorts/weekly-buyer-going-quiet")).andExpect(status().isNoContent());
        mvc.perform(get("/cohorts/weekly-buyer-going-quiet")).andExpect(status().isNotFound());
    }

    @Test
    void invalidRuleReturns400WithPosition() throws Exception {
        mvc.perform(post("/cohorts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Broken", "priority": 1, "rule": "orders_30d >= 4 AND oders_7d > 1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid rule"))
                .andExpect(jsonPath("$.position").value(20))
                .andExpect(jsonPath("$.detail").value(containsString("did you mean 'orders_7d'")));
    }

    @Test
    void validateEndpointDoesNotSave() throws Exception {
        mvc.perform(post("/rules/validate").contentType(MediaType.APPLICATION_JSON).content("""
                        {"rule": "first_order_date within_days 14"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeDependent").value(true));
        mvc.perform(get("/cohorts")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void evaluateFromOrdersExplainsAndStoresTheResult() throws Exception {
        createCohort("""
                {"name": "Dairy regular", "priority": 10, "rule": "dairy_orders_30d >= 2"}""");
        createCohort("""
                {"name": "Quiet", "priority": 20, "rule": "days_since_last_order > 14"}""");

        mvc.perform(post("/users/asha/evaluate?asOf=2026-11-01").contentType(MediaType.APPLICATION_JSON).content("""
                        {"city": "Bengaluru",
                         "orders": [
                           {"date": "2026-10-20", "amountPaisa": 45000, "categories": ["dairy", "produce"]},
                           {"date": "2026-10-28", "amountPaisa": 30000, "categories": ["dairy"]}
                         ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-11-01"))
                .andExpect(jsonPath("$.cohorts", hasSize(1)))
                .andExpect(jsonPath("$.cohorts[0].id").value("dairy-regular"))
                .andExpect(jsonPath("$.cohorts[0].explanation.label").value("dairy_orders_30d >= 2"))
                .andExpect(jsonPath("$.cohorts[0].explanation.detail").value("actual: 2"));

        mvc.perform(get("/users/asha/cohorts"))
                .andExpect(jsonPath("$.cohorts[*].cohortId", hasItem("dairy-regular")))
                .andExpect(jsonPath("$.cohorts", hasSize(1)));
    }

    @Test
    void priorityModeReturnsOneCohortAndAttributesCanBeGivenDirectly() throws Exception {
        createCohort("""
                {"name": "Low", "priority": 1, "rule": "orders_30d >= 1"}""");
        createCohort("""
                {"name": "High", "priority": 9, "rule": "orders_30d >= 3"}""");

        mvc.perform(post("/users/u1/evaluate?mode=priority").contentType(MediaType.APPLICATION_JSON).content("""
                        {"attributes": {"orders_30d": 5}}"""))
                .andExpect(jsonPath("$.mode").value("priority"))
                .andExpect(jsonPath("$.cohorts", hasSize(1)))
                .andExpect(jsonPath("$.cohorts[0].id").value("high"));
    }

    @Test
    void badAttributesAndCategoriesAre400() throws Exception {
        mvc.perform(post("/users/u1/evaluate").contentType(MediaType.APPLICATION_JSON).content("""
                        {"attributes": {"orders_30d": "lots"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("attribute 'orders_30d' must be a NUMBER"));

        mvc.perform(post("/users/u1/evaluate").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orders": [{"date": "2026-10-20", "amountPaisa": 100, "categories": ["toys"]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("unknown category 'toys'")));

        mvc.perform(post("/users/u1/evaluate?mode=sometimes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
