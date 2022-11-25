package tech.meliora.mulika.monitoring.amq;

import java.util.ArrayList;
import java.util.List;

public class Configuration {


    private String url;

    private List<JMXConfig> configs;

    public Configuration() {
        configs = new ArrayList<>();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<JMXConfig> getConfigs() {
        return configs;
    }

    public void setConfigs(List<JMXConfig> configs) {
        this.configs = configs;
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "url='" + url + '\'' +
                ", configs=" + configs +
                '}';
    }
}
