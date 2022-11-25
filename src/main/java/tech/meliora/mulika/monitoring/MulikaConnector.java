package tech.meliora.mulika.monitoring;

import tech.meliora.mulika.monitoring.contant.MulikaConstants;
import tech.meliora.mulika.monitoring.dto.MulikaServiceDTO;
import tech.meliora.mulika.monitoring.enumeration.ServiceType;
import tech.meliora.mulika.monitoring.util.HTTPClient;
import tech.meliora.mulika.monitoring.util.HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MulikaConnector {
    private static final Logger log = LoggerFactory.getLogger(MulikaConnector.class);
    public static Map<String, MulikaServiceDTO> servicesMap = new HashMap<>();

    private String mulikaUrl;
    private String mulikaAPIKey;

    private String app;

    private int reportInterval;

    private String module;

    private Thread mulikaThread;

    @PostConstruct
    public void init() {
        log.info("About to start stats push thread");
        mulikaThread = new Thread(() -> {
            while (true) {
                try {
                    try {
                        Thread.sleep(reportInterval);
                    } catch (InterruptedException ex) {
                        log.warn("Thread could not sleep. trying again", ex);
                        Thread.sleep(reportInterval);
                    }

                    reportStats();

                } catch (InterruptedException e) {
                    log.error("received an interrupt signal", e);
                    break;
                } catch (Exception ex) {
                    log.warn("Encountered exception. Proceeding", ex);
                }
            }
        }, "mulika-thread");

        log.info("Successfully initialized mulika thread");

        mulikaThread.start();

        // other configs
        mulikaUrl = System.getenv(MulikaConstants.MONITORING_URL_KEY);

        if (mulikaUrl == null || mulikaUrl.trim().isEmpty()) {
            mulikaUrl = MulikaConstants.MONITORING_URL;
        }

        mulikaAPIKey = System.getenv(MulikaConstants.MONITORING_API_KEY);

        if (mulikaAPIKey == null || mulikaAPIKey.trim().isEmpty()) {
            mulikaAPIKey = MulikaConstants.API_KEY;
        }

        log.info("Successfully started mulika thread. mulikaUrl = {}, mulikaAPIKey = {}", mulikaUrl, mulikaAPIKey);


        app = System.getenv(MulikaConstants.MONITORING_APP_NAME_KEY);
        if (app == null || app.trim().isEmpty()) {
            app = MulikaConstants.APP_NAME;
        }

        module = System.getenv(MulikaConstants.MONITORING_APP_MODULE_KEY);
        if (module == null || module.trim().isEmpty()) {
            module = MulikaConstants.MODULE_NAME;
        }

        log.info("app name = {}, module name = {}", app, module);

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

    }

    @PreDestroy
    public void destroy() {
        log.info("About to interrupt mulikaThread");

        mulikaThread.interrupt();

        log.info("Successfully interrupted mulikaThread");
    }

    public static void report(String className, String method, boolean successful, int transactionTime, int queueSize) {
        log.debug("Request to report class : {}, method : {}, result : {}, transactionTime : {}", className,
                method, successful, transactionTime);

        String serviceName = getServiceName(className, method);
        MulikaServiceDTO mulikaServiceDTO = servicesMap.get(serviceName);

        if (mulikaServiceDTO == null) {
            mulikaServiceDTO = new MulikaServiceDTO(ServiceType.SERVICE,
                    serviceName, 0, 0, 0, 0, 0);
            servicesMap.put(serviceName, mulikaServiceDTO);
        }

        mulikaServiceDTO.addRequest(successful, transactionTime, queueSize);

        log.info("Successfully reported: class: {} method : {}, result : {}, transactionTime : {}, " +
                "service : {}, map: {}", className, method, successful, transactionTime, mulikaServiceDTO, servicesMap);
    }

    public static void reportQueue(String queueName, boolean successful, int transactionTime, int queueSize) {
        log.debug("Request to report class : {}, method : {}, result : {}, transactionTime : {}", queueName,
                successful, transactionTime);

        String serviceName = queueName;
        MulikaServiceDTO mulikaServiceDTO = servicesMap.get(serviceName);

        if (mulikaServiceDTO == null) {
            mulikaServiceDTO = new MulikaServiceDTO(ServiceType.SERVICE,
                    serviceName, 0, 0, 0, 0, 0);
            servicesMap.put(serviceName, mulikaServiceDTO);
        }

//        mulikaServiceDTO.addRequest(successful, transactionTime, queueSize);
        mulikaServiceDTO.setQueueSize(queueSize);
        ; //increment queue size only

        log.info("Successfully reported: queue: {}, result : {}, transactionTime : {}, " +
                "service : {}, map: {}", queueName, successful, transactionTime, mulikaServiceDTO, servicesMap);
    }

    public static void reportClient(String serviceName, boolean successful, int transactionTime, int queueSize) {
        log.debug("Request to report service {}, result : {}, transactionTime : {}", serviceName, successful, transactionTime);

        MulikaServiceDTO mulikaServiceDTO = servicesMap.get(serviceName);

        if (mulikaServiceDTO == null) {
            mulikaServiceDTO = new MulikaServiceDTO(ServiceType.CLIENT,
                    serviceName, 0, 0, 0, 0, 0);
            servicesMap.put(serviceName, mulikaServiceDTO);
        }

        mulikaServiceDTO.addRequest(successful, transactionTime, queueSize);

        log.info("Successfully reported: service: {}, result : {}, transactionTime : {}, " +
                "service : {}, map: {}", serviceName, successful, transactionTime, mulikaServiceDTO, servicesMap);
    }

    private static String getServiceName(String className, String method) {
        if (className != null && method != null) {
            return className + "." + method; // AccountResource.getAccount
        }

        return method;
    }

    private void reportStats() {
        try {
            String jsonRequest = getRequests();

            if (jsonRequest != null) {

                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + mulikaAPIKey.trim());
                headers.put("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; " +
                        "en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");

                log.info("Request to send to mulika  {}, url {}", jsonRequest, mulikaUrl);

                HTTPResponse response = HTTPClient.send(mulikaUrl, jsonRequest, "POST",
                        "application/json", headers, 5000, 120000);

                log.info("mulika|" + this.app + "|" + this.module + "|request :" + jsonRequest
                        + "|response : " + response + "|stats sent");

            } else {
                log.info("mulika|" + this.app + "|" + this.module + "|request :" + jsonRequest
                        + "|no data to be sent, the service map is empty");
            }


        } catch (IOException e) {
            log.info("mulika|" + this.app + "|" + this.module + ". Encountered exception", e);
        }
    }

    private String getRequests() throws JsonProcessingException {


        if (servicesMap.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> mapList = new ArrayList<>();

        for (MulikaServiceDTO service : servicesMap.values()) {
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("id", service.getName());
            requestMap.put("name", service.getName());
            requestMap.put("type", service.getType());
            requestMap.put("applicationName", app);
            requestMap.put("moduleName", module);
            requestMap.put("transactionTime", service.getAvgTransactionTime());
            requestMap.put("totalRequests", service.getTotalRequests());
            requestMap.put("successTotal", service.getSuccessTotal());
            requestMap.put("rejectedMessages", service.getRejectedMessages());
            requestMap.put("queueSize", service.getQueueSize());

            service.resetCounters();

            mapList.add(requestMap);
        }

        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.writeValueAsString(mapList);
    }


}
