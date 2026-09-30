package com.deepak.misclab.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;

/**
 * Needs: Vite dev server on host :5173 and the backend on host :8090.
 * Chrome runs inside a container, so it reaches the host via host.testcontainers.internal.
 */
@org.testcontainers.junit.jupiter.Testcontainers
class ReceiptFormIT {

    static {
        // Must run before the browser container starts. A static block, not @BeforeAll:
        // the Testcontainers extension starts @Container fields before @BeforeAll methods run.
        Testcontainers.exposeHostPorts(5173, 8090);
    }

    @Container
    static BrowserWebDriverContainer<?> chrome =
            // selenium/standalone-chromium is multi-arch (runs natively on Apple silicon); standalone-chrome is amd64-only
            new BrowserWebDriverContainer<>(DockerImageName.parse("selenium/standalone-chromium:4.27.0")
                    .asCompatibleSubstituteFor("selenium/standalone-chrome"))
                    .withCapabilities(new ChromeOptions());

    @Test
    void createsReceiptThroughTheUi() {
        RemoteWebDriver driver = new RemoteWebDriver(chrome.getSeleniumAddress(), new ChromeOptions());
        try {
            String row = new ReceiptFormPage(driver)
                    .open("http://host.testcontainers.internal:5173")
                    .create("Selenium Sam", "1000", "INR")
                    .waitForReceiptContaining("Selenium Sam");
            assertTrue(row.contains("1000"), row);
        } finally {
            driver.quit();
        }
    }
}
