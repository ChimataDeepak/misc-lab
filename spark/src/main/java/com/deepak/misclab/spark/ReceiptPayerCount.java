package com.deepak.misclab.spark;

import java.util.Map;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

/**
 * Local Spark word-count of receipt payers ({@code payer amount} lines). Not Databricks/Hadoop/Airflow.
 */
public class ReceiptPayerCount {

    public static Map<String, Long> countPayers(String inputPath) {
        SparkConf conf = new SparkConf()
                .setAppName("misc-lab-receipt-index")
                .setMaster("local[1]")
                .set("spark.ui.enabled", "false")
                .set("spark.driver.host", "127.0.0.1");
        try (JavaSparkContext sc = new JavaSparkContext(conf)) {
            return sc.textFile(inputPath)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.trim().split("\\s+")[0])
                    .countByValue();
        }
    }
}
