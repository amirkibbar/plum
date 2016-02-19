package ajk.consul4spring;

import org.apache.commons.logging.Log;
import org.springframework.stereotype.Component;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.PTRRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.springframework.util.StringUtils.collectionToCommaDelimitedString;
import static org.xbill.DNS.ReverseMap.fromAddress;
import static org.xbill.DNS.Type.A;
import static org.xbill.DNS.Type.PTR;
import static org.xbill.DNS.Type.SRV;
import static org.xbill.DNS.Type.TXT;

/**
 * a convenient way to resolve SRV records in a DNS
 */
@SuppressWarnings("unused")
@Component
public class DnsResolver {
    private Log log = getLog(getClass());
    private String nonLoopback;

    /**
     * resolves an SRV record by its name and the default resolution defined at the host level
     *
     * @param name the DNS name of the SRV record
     * @return a comma separate list of ip:port, e.g: 1.2.3.4:8080,2.3.4.5:9090 or null when unable to resolve
     */
    public String resolveServiceByName(String name) {
        return resolveSrvByName(null, name);

    }

    /**
     * resolves an A record by its name using a specified DNS host and port
     *
     * @param resolverHost name server hostname or IP address
     * @param resolverPort name server port
     * @param name         the DNS name of the A record - the name to resolve
     * @return a comma separated list of IP addresses or an empty string when unable to resolve
     */
    public String resolveHostByName(String resolverHost, int resolverPort, String name) {
        try {
            SimpleResolver resolver = new SimpleResolver(resolverHost);
            resolver.setPort(resolverPort);

            Lookup lookup = new Lookup(name, A);
            Record[] records = lookup.run();
            if (records != null) {
                List<String> addresses =
                        of(records)
                                .filter(it -> it instanceof ARecord)
                                .map(it -> ((ARecord) it).getAddress().getHostAddress())
                                .collect(toList());

                return collectionToCommaDelimitedString(addresses);
            } else {
                return "";
            }
        } catch (UnknownHostException | TextParseException e) {
            log.warn("unable to resolve using A record " + name, e);
            return "";
        }
    }

    /**
     * resolves the TXT field for a given name using a specified DNS host and port. This is useful, for example,
     * if you want to resolve abusers with: <a href="https://abusix.com/contactdb.html">https://abusix.com/contactdb.html</a>
     *
     * @param resolverHost name server hostname or IP address
     * @param resolverPort name server port
     * @param name         the DNS name of the TXT record - the name to resolve
     * @return the resolved text
     */
    public String resolveTextByName(String resolverHost, int resolverPort, String name) {
        try {
            SimpleResolver resolver = new SimpleResolver(resolverHost);
            resolver.setPort(resolverPort);

            Lookup lookup = new Lookup(name, TXT);
            Record[] records = lookup.run();
            if (records != null) {
                List<String> addresses =
                        of(records)
                                .filter(it -> it instanceof TXTRecord)
                                .map(it -> collectionToCommaDelimitedString(((TXTRecord) it).getStrings()))
                                .collect(toList());

                return collectionToCommaDelimitedString(addresses);
            } else {
                return "";
            }
        } catch (UnknownHostException | TextParseException e) {
            log.warn("unable to resolve using TXT record " + name, e);
            return "";
        }
    }

    /**
     * reverse lookup an IP address using a specified DNS host and port
     *
     * @param resolverHost name server hostname or IP address
     * @param resolverPort name server port
     * @param address      the IP address to reverse lookup
     * @return a comma separated list of names or an empty string when unable to resolve
     */
    public String reverseLookupByAddress(String resolverHost, int resolverPort, InetAddress address) {
        try {
            SimpleResolver resolver = new SimpleResolver(resolverHost);
            resolver.setPort(resolverPort);

            Lookup lookup = new Lookup(fromAddress(address), PTR);
            Record[] records = lookup.run();
            if (records != null) {
                List<String> addresses =
                        of(records)
                                .filter(it -> it instanceof PTRRecord)
                                .map(it -> ((PTRRecord) it).getTarget().toString())
                                .collect(toList());

                return collectionToCommaDelimitedString(addresses);
            } else {
                return "";
            }
        } catch (UnknownHostException e) {
            log.warn("unable to resolve using SRV record " + address, e);
            return "";
        }
    }

    /**
     * resolves an SRV record by its name using a specified DNS host and port
     *
     * @param resolverHost name server hostname or IP address
     * @param resolverPort name server port
     * @param name         the DNS name of the SRV record
     * @return a comma separate list of ip:port, e.g: 1.2.3.4:8080,2.3.4.5:9090 or null when unable to resolve
     */
    public String resolveServiceByName(String resolverHost, int resolverPort, String name) {
        try {
            SimpleResolver resolver = new SimpleResolver(resolverHost);
            resolver.setPort(resolverPort);

            return resolveSrvByName(resolver, name);
        } catch (UnknownHostException e) {
            log.warn("unable to resolve using SRV record " + name, e);
            return null;
        }
    }

    private String resolveSrvByName(Resolver resolver, String name) {
        try {
            Lookup lookup = new Lookup(name, SRV);
            if (resolver != null) {
                lookup.setResolver(resolver);
            }
            Record[] records = lookup.run();
            if (records == null) {
                return null;
            }

            return of(records)
                    .filter(it -> it instanceof SRVRecord)
                    .map(srv -> resolveHostByName(resolver, ((SRVRecord) srv).getTarget()) + ":" + ((SRVRecord) srv).getPort())
                    .distinct()
                    .collect(joining(","));
        } catch (TextParseException e) {
            log.warn("unable to resolve using SRV record " + name, e);
            return null;
        }
    }

    /**
     * read the local real IP address (not the loopback address)
     *
     * @return the real IP address, or the loopback address when no real one could be found
     */
    public String readNonLoopbackLocalAddress() {
        if (nonLoopback == null) {
            try {
                Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
                while (nics.hasMoreElements()) {
                    NetworkInterface nic = nics.nextElement();
                    Enumeration<InetAddress> addresses = nic.getInetAddresses();

                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            nonLoopback = addr.getHostAddress();
                            break;
                        }
                    }
                }

                if (nonLoopback == null) {
                    nonLoopback = InetAddress.getLocalHost().getHostAddress();
                }
            } catch (SocketException | UnknownHostException e) {
                log.info("unable to obtain a non loopback local address", e);
            }
        }

        return nonLoopback;
    }

    private String resolveHostByName(Resolver resolver, Name target) {
        Lookup lookup = new Lookup(target, A);
        if (resolver != null) {
            lookup.setResolver(resolver);
        }
        Record[] records = lookup.run();
        Optional<InetAddress> address = of(records)
                .filter(it -> it instanceof ARecord)
                .map(a -> ((ARecord) a).getAddress())
                .findFirst();
        if (address.isPresent()) {
            return address.get().getHostAddress();
        } else {
            log.warn("unknown name: " + target);
            return null;
        }
    }
}
