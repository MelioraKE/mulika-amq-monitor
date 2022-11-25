package tech.meliora.mulika.monitoring.amq;

public class JMXConfig {


    private String name ;
    private String object;
    private String attribute;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    @Override
    public String toString() {
        return "JMXConfig{" +
                "name='" + name + '\'' +
                ", object='" + object + '\'' +
                ", attribute='" + attribute + '\'' +
                '}';
    }
}
