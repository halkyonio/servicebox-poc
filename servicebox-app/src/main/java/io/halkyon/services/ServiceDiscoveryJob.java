package io.halkyon.services;

import java.util.List;
import java.util.regex.Pattern;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.jboss.logging.Logger;

import io.halkyon.model.Cluster;
import io.halkyon.model.Service;
import io.quarkus.scheduler.Scheduled;

/**
 * The service discovery job will loop over the registered services and clusters and check whether a service is deployed in
 * a cluster. If so, it will update the service entity deployed and cluster fields accordingly.
 */
@ApplicationScoped
public class ServiceDiscoveryJob {

    private static Logger LOG = Logger.getLogger(ServiceDiscoveryJob.class);

    @Inject
    KubernetesClientService kubernetesClientService;

    /**
     * This method will be executed at every `${servicebox.discovery-service-job.poll-every}`.
     * First, it will collect the list of all services and clusters, and then will loop over the services to check whether
     * the service name (from the first part of the service.endpoint field) is installed in one cluster. If so, then it will
     * check whether service port (from the second part of the service.endpoint field) is declared in the found kubernetes
     * service resource.
     */
    @Transactional
    @Scheduled(every="${servicebox.discovery-service-job.poll-every}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void execute() {
        List<Service> services = Service.listAll();
        for (Service service : services) {
            checkService(service);
        }
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void checkCluster(Cluster cluster) {
        List<Service> services = Service.listAll();
        for (Service service : services) {
            if (service.cluster == null && isServiceRunningInCluster(service, cluster)) {
                service.deployed = true;
                service.cluster = cluster;
                cluster.services.add(service);
                service.persist();
            }
        }

        cluster.persist();
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void checkService(Service service) {
        if (service.cluster == null || !isServiceRunningInCluster(service, service.cluster)) {
            service.deployed = false;
            List<Cluster> clusters = Cluster.listAll();
            for (Cluster cluster : clusters) {
                if (isServiceRunningInCluster(service, cluster)) {
                    service.deployed = true;
                    service.cluster = cluster;
                    cluster.services.add(service);
                    break;
                }
            }

            service.persist();
        }
    }

    private boolean isServiceRunningInCluster(Service service, Cluster cluster) {
        try {
            String[] parts = service.endpoint.split(Pattern.quote(":"));
            String serviceName = parts[0];
            String servicePort = parts[1];

            return kubernetesClientService.isServiceRunningInCluster(cluster, serviceName, servicePort);
        } catch (Exception ex) {
            LOG.error("Error trying to discovery the service " + service.id + " in the registered clusters", ex);
        }

        return false;
    }
}
