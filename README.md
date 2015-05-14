# consul4spring
A spring-boot extension aimed at using Consul as a micro service registration

# Overview
This library is some glue-code that I needed to write my first micro service. I wanted my micro service to use 
spring-boot and to register itself in a [Consul](https://consul.io/) cluster. This library allows a spring-boot 
application to:

- Automatically register itself as a service in Consul
- Register default properties in the Consul key value store
- Mark custom Consul checks as passed/failed
- Use the Consul distributed lock
- Easily store values in the Consul key value store
- Resolve a DNS SRV record

# Using the library features

## Setup

### Setup your gradle project

```
    
    repositories {
        maven {
            url  "http://dl.bintray.com/amirk/maven" 
        }
    }
    
    dependencies {
        compile "ajk.plum:plum:0.0.2"
    }
```

### Define the Consul properties

In your application.yml, or in any other way supported by spring-boot, define the following properties

```
    consul:
      hostname: consul
      httpPort: 8500
      dnsPort: 8600
      serviceId: my-service-id
      serviceName: PrettyName
      tags: some, tags
```

### Define the "default properties"

This library allows you to setup a "default properties" object. This object will be registered in the Consul key value
store when your application starts. This object can be anything, as long as it's possible to serialize and deserialize
it as JSON. To define your default properties object just annotate anything in the classpath with @DefaultProperties:

```
    
    import ajk.consul4spring.DefaultProperties
    
    @DefaultProperties
    public class MyProperties {
      ...
    }
```

### Activate the consul profile

When your application is started with the "consul" spring profile active then this library will register the a 
ConsulTemplate bean, a DistributedLock bean and a DnsResolver. If this profile is not active then these services will
be registered with a "fake" implementation that doesn't use Consul. This allows you to run your application regardless
of the existence of Consul in the environment if you wish.

## Registration as a Consul service

The library will register your application as Consul service and add a heartbeat check for this service. The heartbeat
ID in Consul will be <serviceId>-heartbeat@<service-hostname>:<port>. The library will automatically detect the hostname
on which your service is running and the port.

The heartbeat check in consul is set with a 20 seconds TTL, and a scheduled job in the library updates the heartbeat
every 10 seconds.

## Marking Consul checks

You may want to add some custom health checks to Consul to reflect the status of your service. To do this use the 
CheckService:

```
    
    public class MyHealthMonitor {
      @Autowire
      private CheckService checkService;
      
      public void myHealthReport() {
        if(something) {
          checkService.pass("something", 2000 /* TTL in milliseconds */, "notes");
        } else {
          checkService.fail("something", 2000 /* TTL in milliseconds */, "notes");
        }
      }
    }
```

## Using the distributed lock

Consul provides a convenient distributed lock mechanism. This library exposes this mechanism in an easy to use way:

```
    
    public class MyService {
      @Autowire
      private DistributedLock lock;
        
      public void myCriticalCode() {
        String lockId = lock.acquire();
        if (lockId == null) {
          // couldn't obtain the lock - deal with it
          return;
        }
        
        try {
          // critical code
        } finally {
          lock.release(lockId);
        }
      }
    }
```

## Easily store and retrive values from the Consul key value store

Use the ConsulTemplate to retrieve and store values in the Consul key value store:

```

    public class MyConsulRepository {
        @Autowire
        private ConsulTemplate consulTemplate;
        
        public void storeSomething(String value) {
          consulTemplate.store("/key", value);
        }
        
        public String readStringValue() {
          return consulTemplate.find("/key");
        }
        
        public MyObject readMyObject() {
          return consulTemplate.findAndConvert(MyObject.class, "/key");
        }
    }
    
```

## Resolve DNS SRV records

Use the DnsResolver to get a "cluster" definition of a service. A cluster definition is a comma separated list of 
host:port. For example, let's say that you want to resolve the location of all the nodes in a RabbitMQ cluster:

```

    public class MyRabbitUsingClass {
        @Autowire
        private DnsResolver dnsResolver;
        
        public void resolveRabbit() {
            System.out.println(dnsResolver.resolveServiceByName("rabbit");
        }
    }

```

If the name "rabbit" is configured in your DNS, for example in your Consul as a service, and there are 2 nodes in that
cluster: rabbit-1 with IP 10.0.0.10 on port 5672 and rabbit-2 with IP 10.0.0.20 on port 6672, then the result of the
above query would be: 10.0.0.10:5672,10.0.0.20:6672 - according to the SRV record.