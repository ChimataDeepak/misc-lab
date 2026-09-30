package com.deepak.misclab.e2e;

import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Page Object: the test talks in "create a receipt", not in CSS selectors. */
public class ReceiptFormPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    public ReceiptFormPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public ReceiptFormPage open(String baseUrl) {
        driver.get(baseUrl);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("payer")));
        return this;
    }

    public ReceiptFormPage create(String payer, String amount, String currency) {
        driver.findElement(By.id("payer")).sendKeys(payer);
        driver.findElement(By.id("amount")).sendKeys(amount);
        new Select(driver.findElement(By.id("currency"))).selectByVisibleText(currency);
        driver.findElement(By.id("submit")).click();
        return this;
    }

    public String waitForReceiptContaining(String text) {
        By item = By.xpath("//ul[@id='receipts']/li[contains(., '" + text + "')]");
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(item)).getText();
        } catch (org.openqa.selenium.TimeoutException e) {
            // Show what the user would see (e.g. the app's error banner) instead of a bare timeout.
            throw new AssertionError("Receipt '" + text + "' never appeared. Page text was:\n"
                    + driver.findElement(By.tagName("body")).getText(), e);
        }
    }
}
