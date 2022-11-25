package tech.meliora.mulika.monitoring.amq;

import tech.meliora.mulika.monitoring.MulikaConnector;
import tech.meliora.mulika.monitoring.contant.ActiveMQConstants;
import tech.meliora.mulika.monitoring.contant.MulikaConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class AmqJmxConnector {
    private static final Logger log = LoggerFactory.getLogger(AmqJmxConnector.class);
    private Thread amqMonitorThread;

    String jmxUrl;
    String configFille = ActiveMQConstants.CONFIG_FILE;

    int reportInterval = 60000; //1 minute default

    Configuration jmxConfigs;

    @PostConstruct
    public void init() {
        log.info("about to poll AMQ stats via JMX");


        log.info("jmx config file: {}", configFille);

        try {

            jmxConfigs = loadJMXConfigs();

            log.info("jmx configs: {} ", jmxConfigs);

        } catch (IOException e) {
            log.error("error loading file", e);

            jmxConfigs = new Configuration();
        }

        try {
            if (System.getenv(MulikaConstants.MONITORING_REPORT_INTERVAL_KEY) != null) {
                reportInterval = Integer.parseInt(System.getenv(MulikaConstants.MONITORING_REPORT_INTERVAL_KEY));
            } else {
                reportInterval = MulikaConstants.REPORT_INTERVAL;
            }
        } catch (Exception ex) {
            reportInterval = MulikaConstants.REPORT_INTERVAL;
        }

        log.info("mulika reportInterval = {}", reportInterval);


        amqMonitorThread = new Thread(() -> {
            while (true) {
                try {
                    try {
                        Thread.sleep(reportInterval);
                    } catch (InterruptedException ex) {
                        log.warn("Thread could not sleep. trying again", ex);
                        Thread.sleep(reportInterval);
                    }

                    pollAndReportStats();

                } catch (InterruptedException e) {
                    log.error("received an interrupt signal", e);
                    break;
                } catch (Exception ex) {
                    log.warn("Encountered exception. Proceeding", ex);
                }
            }
        }, "amq-jmx-queue-monitor");

        log.info("Successfully initialized mulika thread");

        amqMonitorThread.start();

    }

    @PreDestroy
    public void destroy() {
        log.info("About to interrupt mulikaThread");

        amqMonitorThread.interrupt();

        log.info("Successfully interrupted mulikaThread");
    }

    private void pollAndReportStats() {

        try {

            JMXServiceURL url = new JMXServiceURL(jmxConfigs.getUrl());
            JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
            MBeanServerConnection mbeanServerConn = jmxc.getMBeanServerConnection();


            log.info("connected to jmx server = {}", jmxUrl);


            for (JMXConfig jmxConfig : jmxConfigs.getConfigs()) {

                try{

                    ObjectName objectName = new ObjectName(jmxConfig.getObject());

                    int value = Integer.parseInt(
                            mbeanServerConn.getAttribute(objectName, jmxConfig.getAttribute())+""); //convert value to string and then parse

                    log.info("retrieved jmx value for {} = {}", jmxConfig, value);

                    MulikaConnector.reportQueue(jmxConfig.getName(), true, 0, value);

                } catch (Exception ex){
                    log.error("error getting jmx queue stats file "+ jmxConfig, ex);
                }

            }

        } catch (Exception e) {

            log.error("error getting jmx queue stats file", e);


        }


    }


    private Configuration loadJMXConfigs() throws IOException {
        Configuration jmxConfigs;
        String content = readFromInputStream(getFileFromResourceAsStream(configFille));

        log.info("jmx config file content {} ", content);

        ObjectMapper objectMapper = new ObjectMapper();
        jmxConfigs = objectMapper.readValue(content, Configuration.class);

        return jmxConfigs;
    }


    private InputStream getFileFromResourceAsStream(String fileName) {

        // The class loader that loaded the class
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(fileName);

        // the stream holding the file content
        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            return inputStream;
        }

    }

    private static String readFromInputStream(InputStream is) throws IOException {

        StringBuilder content = new StringBuilder();

        try (InputStreamReader streamReader =
                     new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(streamReader)) {

            String line;
            while ((line = reader.readLine()) != null) {

                content.append(line);

            }

        }

        return content.toString();

    }


}
