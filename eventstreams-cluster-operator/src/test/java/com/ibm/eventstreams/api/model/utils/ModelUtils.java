/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2019  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */

package com.ibm.eventstreams.api.model.utils;

import com.ibm.eventstreams.api.Endpoint;
import com.ibm.eventstreams.api.EndpointServiceType;
import com.ibm.eventstreams.api.ProductUse;
import com.ibm.eventstreams.api.TlsVersion;
import com.ibm.eventstreams.api.model.AbstractSecureEndpointsModel;
import com.ibm.eventstreams.api.model.EventStreamsKafkaModel;
import com.ibm.eventstreams.api.model.GeoReplicatorDestinationUsersModel;
import com.ibm.eventstreams.api.model.GeoReplicatorSourceUsersModel;
import com.ibm.eventstreams.api.spec.EventStreams;
import com.ibm.eventstreams.api.spec.EventStreamsBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsGeoReplicatorBuilder;
import com.ibm.eventstreams.api.spec.EventStreamsSpec;
import com.ibm.eventstreams.api.spec.SecurityComponentSpec;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaSpecBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListeners;
import io.strimzi.api.kafka.model.listener.KafkaListenersBuilder;
import io.strimzi.operator.cluster.model.AbstractModel;
import io.strimzi.operator.cluster.model.Ca;
import io.strimzi.operator.common.model.Labels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

public class ModelUtils {

    public static EventStreamsBuilder createDefaultEventStreams(String instanceName) {
        return new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(instanceName).build())
                .withNewSpec()
                    .withNewLicense()
                        .withAccept(true)
                        .withUse(ProductUse.CP4I_PRODUCTION)
                    .endLicense()
                .endSpec();
    }

    public static EventStreamsBuilder createEventStreams(String instanceName, EventStreamsSpec eventStreamsSpec) {
        return new EventStreamsBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(instanceName).build())
                .withNewSpecLike(eventStreamsSpec)
                .endSpec();

    }

    public static EventStreamsGeoReplicatorBuilder createDefaultEventStreamsGeoReplicator(String instanceName) {
        return new EventStreamsGeoReplicatorBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(instanceName).build())
                .withNewSpec()
                .withReplicas(1)
                .endSpec();
    }

    public static EventStreamsBuilder createEventStreamsWithAuthentication(String instanceName) {
        return ModelUtils.createDefaultEventStreams(instanceName)
            .withMetadata(new ObjectMetaBuilder()
                .withNewName(instanceName)
                .build())
            .editSpec()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                    .withNewListeners()
                        .withNewPlain()
                            .withNewKafkaListenerAuthenticationTlsAuth()
                            .endKafkaListenerAuthenticationTlsAuth()
                        .endPlain()
                    .endListeners()
                .endKafka()
            .build())
            .endSpec();
    }

    public static EventStreamsBuilder createEventStreamsWithAuthorization(String instanceName) {
        return ModelUtils.createDefaultEventStreams(instanceName)
            .withMetadata(new ObjectMetaBuilder()
                .withNewName(instanceName)
                .build())
            .editSpec()
            .withStrimziOverrides(new KafkaSpecBuilder()
                .withNewKafka()
                    .withNewListeners()
                        .withNewPlain()
                            .withNewKafkaListenerAuthenticationTlsAuth()
                            .endKafkaListenerAuthenticationTlsAuth()
                        .endPlain()
                    .endListeners()
                    .withNewKafkaAuthorizationRunAs()
                    .endKafkaAuthorizationRunAs()
                .endKafka()
            .build())
            .endSpec();
    }

    public static void assertCorrectImageOverridesOnContainers(List<Container> containers, Map<String, String> imageOverrides) {
        containers.forEach(container -> {
            String image = container.getImage();
            String name = container.getName();
            String expectedImage = imageOverrides.get(name);
            assertThat("Container " + name, image, is(expectedImage));
        });
    }

    public static Map<String, String> mockCommonServicesClusterData() {
        Map<String, String> data = new HashMap<>();
        data.put("cluster_name", "mycluster");
        data.put("cluster_endpoint", "ingress");
        data.put("cluster_address", "consoleHost");
        data.put("cluster_router_https_port", "443");
        return data;
    }

    public enum Certificates {
        CLUSTER_CA,
        NEW_CLUSTER_CA;

        @Override
        public String toString() {
            switch (this) {
                case CLUSTER_CA:
                    return "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURPekNDQWlPZ0F3SUJBZ0lVQmtMbTdGaFowODVSbkwyQ1ZMRmRySElVK1ljd0RRWUpLb1pJaHZjTkFRRUwKQlFBd0xURVRNQkVHQTFVRUNnd0thVzh1YzNSeWFXMTZhVEVXTUJRR0ExVUVBd3dOWTJ4MWMzUmxjaTFqWVNCMgpNREFlRncweU1EQXhNVE14TVRJeU16ZGFGdzB5TVRBeE1USXhNVEl5TXpkYU1DMHhFekFSQmdOVkJBb01DbWx2CkxuTjBjbWx0ZW1reEZqQVVCZ05WQkFNTURXTnNkWE4wWlhJdFkyRWdkakF3Z2dFaU1BMEdDU3FHU0liM0RRRUIKQVFVQUE0SUJEd0F3Z2dFS0FvSUJBUURUR1dRYm13TjdLVXV6TmRYaUhEYXJjcU1xRm9jTGJzcDd3azloaDc4ZgpYTVhaWWtodkdETTU0TzNuc21Ua3paamR3MjRtVjcxZENBYS9tK3dRZE5ZcVBWaEI1TXpiQVUreVlVZkR6YXA4Ckl2QXhTb3dJejJ0aTVEMTJZTktJVEx0bVltbktWYU4yWkdqb2lkV0FlK2VRWnZXQ3dKQXJBSmYxY09VbEJPdUIKbzhwQjNaOHhIdmQwSjdjTUJoeXRqZnJ3d2EvRGFQaDlidmNpV0RwODhzdUlwWHcvdkNxYTF4WUloNWJTeDVkSQptd0JZQUhGZGtZVHkxdk9iSm16eFdGU0FKZlB5WkY2alF2bXNlNTZaME9hVlF0c1B1K0VnbWhFVkt6SmR1OXdaCjRnSVFvZjF0cEdCYlZkUjNhOUlrNzdDZXN2Yzl5dC9ER2RpNjFjTWpDOGxSQWdNQkFBR2pVekJSTUIwR0ExVWQKRGdRV0JCUlVzaVdmaHJ5Rkd3aVlhZ1BUL2FxZDkyUTU2ekFmQmdOVkhTTUVHREFXZ0JSVXNpV2ZocnlGR3dpWQphZ1BUL2FxZDkyUTU2ekFQQmdOVkhSTUJBZjhFQlRBREFRSC9NQTBHQ1NxR1NJYjNEUUVCQ3dVQUE0SUJBUUNaCjlieFlJVXVlWHp1eDB2aHA4K0RXcGVTa1R3Qmk3aUJRdzlVNVJlbFZmdzcrTU9ndVp0bEhDRklVZHR5NC9XK2UKMHF4b1ZiZmJJZDlQbnhNT0VLamN6ZEV4NGRlNVFFNDJ6eEtaUUZLcGM5WEUxSXhSM1BXY01mb3NJdjZXR21oVQozZFdmbDI2ZlA1NzE1K21ZanJIMHpmZEFkUlhUYTBZTzdIakhiZUoza0JpNElxWXNqNTllMk9icXBDWGRkRTUyCnZidndWend5SVkwazBHRVhKV3IzdGZnT2Z0R3krNmNOYkJQV3JkSlVnWWV5Q0Z0ODR4c0U5Q01jOXRaL01NZEgKVXdhUGxkUjJGMUtpVjBHWUhsa3BkZlBjWE91NmFtNXhRbEFuU0pYd1VVcUIxY3JUOGNxZ3lmU2tyTklhRzJZcApIRGJpRXVrU1Q3ZFRhR2Z1NHBMRwotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg";
                case NEW_CLUSTER_CA:
                    return "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURyVENDQXBXZ0F3SUJBZ0lKQUpRREcxdWtxeSthTUEwR0NTcUdTSWIzRFFFQkN3VUFNQzB4RXpBUkJnTlYKQkFvTUNtbHZMbk4wY21sdGVta3hGakFVQmdOVkJBTU1EV05zZFhOMFpYSXRZMkVnZGpBd0hoY05NakF3TVRJeQpNVFF6TkRRM1doY05NakV3TVRJeE1UUXpORFEzV2pBMk1STXdFUVlEVlFRS0RBcHBieTV6ZEhKcGJYcHBNUjh3CkhRWURWUVFEREJadGVTMWxjeTFwWW0wdFpYTXRZV1J0YVc0dFlYQnBNSUlCSWpBTkJna3Foa2lHOXcwQkFRRUYKQUFPQ0FROEFNSUlCQ2dLQ0FRRUF5Sk9RazNwb3VtTFZXRHZFZEVtRkVSb2ZhOURLVlE1Z1pPamMzVzlRY3IrQgptUG4reHRBT1E5SGh6U1RabmRjdzFqQURqY1RwVTVPU3FWK3ZaVm9SdzFzU0YxYWZHaE9LbkZqU3BXWUNYN1pmCmp3U3BubW44M2xPUnVtUlZjWjB4QTk2ODB3TjhXZWMwZXJERlNXUjFPWmdidmVGMWVPblNDTEtPTFhQOEYrYnUKT2tSeEZFQjcrSkZvMVNiU1IwajZEM0tPaWNseUwwUDNSWEZGK0h4b2hocUdkWFlKOFVwRHhmTmdxa2pqdjBPVApkSlE4Q0Q1OW0rQlZ1K2pwbVFPQ2lVVUlCSWYvdXpCTW1WdkRUekx6UEhqMUZZRDFETmNDUCtvRlZaOXVjVG1GCjF1YytVbWs5UXZibXMrNlJySURVREV5VGx0akFITEs1ZDZZZVZndnRnd0lEQVFBQm80SEdNSUhETUlIQUJnTlYKSFJFRWdiZ3dnYldDTkcxNUxXVnpMV2xpYlMxbGN5MWhaRzFwYmkxaGNHa3RaWE11WVhCd2N5NXNiMjl3ZVM1dgpjeTVtZVhKbExtbGliUzVqYjIyQ0hXMTVMV1Z6TFdsaWJTMWxjeTFoWkcxcGJpMWhjR2t1WlhNdWMzWmpnaXR0CmVTMWxjeTFwWW0wdFpYTXRZV1J0YVc0dFlYQnBMbVZ6TG5OMll5NWpiSFZ6ZEdWeUxteHZZMkZzZ2hadGVTMWwKY3kxcFltMHRaWE10WVdSdGFXNHRZWEJwZ2hsdGVTMWxjeTFwWW0wdFpYTXRZV1J0YVc0dFlYQnBMbVZ6TUEwRwpDU3FHU0liM0RRRUJDd1VBQTRJQkFRQVR0ZjhacnZwZUh3dEJGV09CY01GSDNRWDVlUHVqZDNNQ1diZEkyUHdjCkdveWpGNm40ZkM5NGJjSjZLaG1KenEvNldSM3cvMHZ0Sk5PMWwzRDFrck8wUGVoVXUrWUJwdWJoQ1VqNWRsZ2sKT2FoeFRHVGozZWdKWmFPL1RDcm5Hdk9acWN1a3VPYU1QR25RdDFCM3JSUnE3dnJKL3cxdlJKU3QxektTQkZ5SApMUkMrckpaQ3hBL3ZXQUVKWmNJTkhWM2t4NlhtL1ZzbEJUSmR0dWpSaDdySFBZMDJ4STdyYjlBRUhEVDU1dWFyCmZYSnYzb0V3dnIzcFVJeU1yazFQTEx5VjZGQkU4Q25sQmF4WndJb1RKSDdPcWEzbEZUcGxPUXhlL0c5V0YzU1kKVTZtb3F5aSsyL2xYVTRML2dZS00wNmVTMmlrVzRGOWJDdWJQbEZ4eWlsTUkKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo";
            }
            return super.toString();
        }
    }

    public enum Keys {
        CLUSTER_CA_KEY,
        NEW_CLUSTER_CA_KEY;

        @Override
        public String toString() {
            switch (this) {
                case CLUSTER_CA_KEY:
                    return "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2Z0lCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktnd2dnU2tBZ0VBQW9JQkFRRFRHV1FibXdON0tVdXoKTmRYaUhEYXJjcU1xRm9jTGJzcDd3azloaDc4ZlhNWFpZa2h2R0RNNTRPM25zbVRrelpqZHcyNG1WNzFkQ0FhLwptK3dRZE5ZcVBWaEI1TXpiQVUreVlVZkR6YXA4SXZBeFNvd0l6MnRpNUQxMllOS0lUTHRtWW1uS1ZhTjJaR2pvCmlkV0FlK2VRWnZXQ3dKQXJBSmYxY09VbEJPdUJvOHBCM1o4eEh2ZDBKN2NNQmh5dGpmcnd3YS9EYVBoOWJ2Y2kKV0RwODhzdUlwWHcvdkNxYTF4WUloNWJTeDVkSW13QllBSEZka1lUeTF2T2JKbXp4V0ZTQUpmUHlaRjZqUXZtcwplNTZaME9hVlF0c1B1K0VnbWhFVkt6SmR1OXdaNGdJUW9mMXRwR0JiVmRSM2E5SWs3N0Nlc3ZjOXl0L0RHZGk2CjFjTWpDOGxSQWdNQkFBRUNnZ0VCQUlBZFl0SVdLL0N0U1ZJRUZFQmIyeG9HUXR3aU8rZEdZQURvRm9FY2YzT3IKUERBSUkrbTRpQzVTUWxCaHhqWE9TVDRkZzFZbDNiaitUQW94dVB6cnk2WnJBSXROTHI2amR4ak5UVjZhRFNPMQptSXh4cjdjUkd4MHpZOUhhUlN1UFZoUjNHYmxBYkNwSUdoczJ1NnAwaUQ3dEhZY3pFc3ZtV2xNTkp5Um1iZitJCnE0anhKMXFhS2tybjFlbXMxZTBzS0hkNjZ2NFg2SFVYN1hkRHFXRmhtZUpXQnRQa2FxM083MHBJK2RnenppbHoKYkk5Z0EzOURVMmM2b3RCUXUrTGFZZmY1UW5yTmN5ZXlhL3IrZXlaMkpwak5wN3V5MmJFVTZ1UGxpem1tdXl5LwpFdGlBelRuR2doT2VOVkRlTWNPOHZUNkVmY2lDaDlKQmtFS2EwTnlObWhrQ2dZRUE5SXgrSnQzb0ZBWVVHUitXCkpuTVk1bXAvTFdOQXhXOHQvRjFZYnZWSnN5TzhzaE1QYWlxZnluekYvSm16ZnpKVEl0ZGozbnlqRVBWbnZSY2cKbG1TRnh5ZmYxYVBHOGpqTVI3b2VlMmdkL2ZjY1UrSUQ2ZTVFdkI1NE12MFF3aGpBajlCYlhoZDVweG1nSXdRcwp2V09VeFZQQ05Ld1BYWFdDMkJPM1hUUkhoS3NDZ1lFQTNQdnNod3BIMWdYSDY0SzhDcy9Sc0Ywekl0M0d0ZkJzCjIrNkJxZk45TkRtMWdwcEhNZStPTjdPQmF3eTRxQi96YUQxU2VRSlV0Yk9pZ3JDKzNwS01raGV5UmF2bmNTVkoKejFvUVJnSkJKVUZNUVNyL3E2K0VSMmRJQkppanArUVozZXZiMkVnbWMydmFiUW1kcmIrR0k3RmxiOENteTIyUgoxbGJNZ1ZzTWtmTUNnWUFFaGNTZmUyTWJXN0ZyZFlZVlYvT1I3MDVDeko4YUI2QldGblBZT1hrUGN3MitUVlB2CnBySWwwSURvMXY3V3oxdHlQWUYvVDliRUxZV3BuWS9ndUNNeE42K1FCK05aLzJybnVLMXZvdEZMTFJLOUNtVUEKSW9QcTVyVmFYQXUvU3kza2V2bjFsNEdNY1pEZ2xPY3U2WFNLNGEycHc3VFZDYU5OMDYrRWJiOFUyUUtCZ1FDeQpOVXRLbjZUTTJQNmZVMSszekY1S3Z1NUZHTnB0ME1USkcrOWZFZWdQWE5hZXl6SE0rR1lWVDJKMzVOdHBZZExXClpsV0RGcmtmaXd6c0hnTGUxUW9kcXBSdWtUSGswZkJURWt0N1djZ1Zkakk3ZjZTTlhNN3RFa0pHeXAxNEFJQkgKc2pRcG1BM0NHT0VkKzVvNEN2THZCOWxJcFBFZHJtL2tqVDBBdUY5THR3S0JnQkxZZVBJYnRLa1UwRzNIYVpBKwpicWhpVGptaW5FbDdQUGZUcG9nRjNiRGoyaGZXVVk5QjVYQ2p4c09WWWFRSTZlenMyL2svNldrSlQ4VFY5ZExuCm9LMUQvSzQveXhyYmg2KzZacW1PdEFjVGc0YnBsYWZ0dnd4OWppWE5Kdm8wOGF6SXRlSUwwM3d5cDc4eWFBRHoKcnVkQkx0MVMrVWZpV2Rpa1lEU0ZKVTA4Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K";
                case NEW_CLUSTER_CA_KEY:
                    return "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2d0lCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktrd2dnU2xBZ0VBQW9JQkFRRElrNUNUZW1pNll0VlkKTzhSMFNZVVJHaDlyME1wVkRtQms2TnpkYjFCeXY0R1krZjdHMEE1RDBlSE5KTm1kMXpEV01BT054T2xUazVLcApYNjlsV2hIRFd4SVhWcDhhRTRxY1dOS2xaZ0pmdGwrUEJLbWVhZnplVTVHNlpGVnhuVEVEM3J6VEEzeFo1elI2CnNNVkpaSFU1bUJ1OTRYVjQ2ZElJc280dGMvd1g1dTQ2UkhFVVFIdjRrV2pWSnRKSFNQb1BjbzZKeVhJdlEvZEYKY1VYNGZHaUdHb1oxZGdueFNrUEY4MkNxU09PL1E1TjBsRHdJUG4yYjRGVzc2T21aQTRLSlJRZ0VoLys3TUV5WgpXOE5QTXZNOGVQVVZnUFVNMXdJLzZnVlZuMjV4T1lYVzV6NVNhVDFDOXVhejdwR3NnTlFNVEpPVzJNQWNzcmwzCnBoNVdDKzJEQWdNQkFBRUNnZ0VBWDF2NDRvVFNBYnMzWHltYkx3WTlJM0pkK2U1Y0k3QWdHQm0xMS9reU1Edm8KSzRpL25sb1FUaUljem1GMHJOeEFOYlRDRzhiQWY2R3JtcWNKUnBCWjNaMFFydFBaOUpYUjBwOVFGcDYzQmpZNApuRkJGTERHTHJVL2grdEgwNVgwc2JvQ1pHV0dRMzFWUzEvbnU4Z08yejlrSFozRW5Yb01iL2xLVjBmcFFvak9yCkc0QTZzYlNVeWF1Q0o4VW5VZHc0bkJUOVBnem1QNERpT2NzQUFVaGtGcGpBd3dmOXdTc2FqVExvWUl5ZUhXajQKZFdwdDB1M1lUWHRHWFJnSjNOUEZVMkYrUjlQTzQrY284VThFSHhSc0NGblJxQXE2eGdLZk41Rm5KTllERi9XKwp3Ky9EOWpWc2laUG9XVUw3dnZ3RDJNZ25TOU1MRGUwa1JTWkpRV1Z1UVFLQmdRRGpaS0dXcVViNG4yempGL2xtCjBYVDFLL3VUVnREUitkMDU1Mm5ucjRtRmp3V1ZpdHhaV2lvY09wV2Y5V2w2WHhsUkExWGY3MjFDazNPRGZQSDEKWlpLaldXTkRFRXNmV2xCK2xMUHBiaHVRZGxmVUpmbFlZZnhGWmRPRy9WUS9CR3RwRU1FNFBJT1drdExvOU83bApLTzhoajVzOXVaV1ArampzcmQ1bVdDajg4d0tCZ1FEaHowaENpTEM2NHppejYzMVNTMEYwY2drTjM4b1NDRWovCjFLa1V1emRkanh0MEhVRlRrdFJuVEs1Zm5MRkh6ZGVmU0RpWkpnUGwzcE5HT1A4VWYvZ3c4ZkVKcmdtM2NwN3MKTzhVcnozQkNhWWpBVEVtT2Z5SUM0cGhCRVF6Q1hSS01HeUhtYTZtbWY4S1FjZHF0STMyeE11VVl4TmRWeWlxawp3YXltdVZNeE1RS0JnUUNUUnljOW5MQUI4Y2dsb2U2QUFLTys4OWpDaWxVLzJwVEZuelBCd3JqUWoxeXZpYnZFCkI1a0VwWkVwaEZybnpsWm9XVCt5aGJGL2tGOGR4N3d6RTdHUktTRVlXNkk4VVZPWFdKcmFVdDE1aUp6RUpFQkcKVlZoK1hrQk55eUJZbkhVeEhJLzQ2NERTOG8rMklJWG1XTWZoTmREM2ZvNzNMTHJYMkprV01uMkJyd0tCZ1FDNQorUFJqVDU1MkhPTTdVd3hBdFpndjVpZE0xTzNna2hCRkd3a3grTXF0ZEVwQkJFTWtLSDVrb1VQUG5RWm93Ny85CkFBY1ZJcmo5SGFXZnBSdDM3N2toM25FTTd0Z3p6T1BVWFptUzdtSmZYL2x2bnFUS0JpeWx3YWR4bHpBeDkyTnEKSG9KNStsdWJ0QWN5M1lJakxHSzlpTlFqNVNJUUZ0T3VJNUFsTSthdlVRS0JnUUNUSFY3WFkwMTB3bEpYV3RabgpBZVY3S210VnNsTDUyMmtxRkExZlZxaDFCVmxvMXdWM1BVOFlKSms2RTVxSEl2cVBYZmUzUzZqdEdlQlVJd1grCk96U0R0Q20raTh2Sk1IblhROVY0RGxXM24xZ1RjUkhxQlNyek1pb1VpVklhK1NWTGVJdDZDOE5TNkJFNzh0eHkKbTB0dURwTGF5R0J4ajdqQktRZ1lyeU1ZQkE9PQotLS0tLUVORCBQUklWQVRFIEtFWS0tLS0tCg";
            }
            return super.toString();
        }
    }

    public static Set<Secret> generateClusterCa(String namespace, String clusterName, String appName, Certificates cert, Keys key) {
        String kafkaInstanceName = EventStreamsKafkaModel.getKafkaInstanceName(clusterName);
        Map<String, String> labels = Labels.forStrimziCluster(kafkaInstanceName)
                .withStrimziKind(Kafka.RESOURCE_KIND)
                .toMap();
        Secret clusterCa = new SecretBuilder()
                .withNewMetadata()
                    .withName(AbstractModel.clusterCaCertSecretName(kafkaInstanceName))
                    .withNamespace(namespace)
                    .addToAnnotations(Ca.ANNO_STRIMZI_IO_CA_CERT_GENERATION, "0")
                    .withLabels(labels)
                .endMetadata()
                .addToData("ca.crt", cert.toString())
                .build();
        Secret clusterCaKey = new SecretBuilder()
                .withNewMetadata()
                    .withName(AbstractModel.clusterCaKeySecretName(kafkaInstanceName))
                    .withNamespace(namespace)
                    .addToAnnotations(Ca.ANNO_STRIMZI_IO_CA_KEY_GENERATION, "0")
                    .withLabels(labels)
                .endMetadata()
                .addToData("ca.key", key.toString())
                .build();
        Set<Secret> initialSecrets = new HashSet<>();
        initialSecrets.add(clusterCa);
        initialSecrets.add(clusterCaKey);
        return initialSecrets;
    }

    public static Set<Secret> generateGeoReplicatorConnectSourceSecret(String namespace, String clusterName, String appName, Certificates cert, Keys key) {

        Map<String, String> labels = Labels.forStrimziCluster(EventStreamsKafkaModel.getKafkaInstanceName(clusterName)).withStrimziKind(Kafka.RESOURCE_KIND).toMap();
        Secret replicatorConnectorSource = new SecretBuilder()
            .withNewMetadata()
                .withName(clusterName + "-" + appName + "-" + GeoReplicatorSourceUsersModel.SOURCE_CONNECTOR_KAFKA_USER_NAME)
                .withNamespace(namespace)
                .addToAnnotations(Ca.ANNO_STRIMZI_IO_CA_KEY_GENERATION, "0")
                .withLabels(labels)
            .endMetadata()
            .addToData("user.key", key.toString())
            .addToData("user.crt", cert.toString())
            .addToData("user.password", "password2")
            .build();

        Set<Secret> replicatorConnectSecrets = new HashSet<>();
        replicatorConnectSecrets.add(replicatorConnectorSource);

        return replicatorConnectSecrets;
    }

    public static Set<Secret> generateGeoReplicatorConnectSecrets(String namespace, String clusterName, String appName, Certificates cert, Keys key) {

        Map<String, String> labels = Labels.forStrimziCluster(EventStreamsKafkaModel.getKafkaInstanceName(clusterName)).withStrimziKind(Kafka.RESOURCE_KIND).toMap();
        Secret replicatorConnectorSource = new SecretBuilder()
            .withNewMetadata()
            .withName(GeoReplicatorDestinationUsersModel.getConnectKafkaUserName(clusterName))
            .withNamespace(namespace)
            .addToAnnotations(Ca.ANNO_STRIMZI_IO_CA_KEY_GENERATION, "0")
            .withLabels(labels)
            .endMetadata()
            .addToData("user.key", key.toString())
            .addToData("user.crt", cert.toString())
            .addToData("user.password", "password2")
            .build();

        Set<Secret> replicatorConnectSecrets = new HashSet<>();
        replicatorConnectSecrets.add(replicatorConnectorSource);

        return replicatorConnectSecrets;
    }

    public static Secret generateSecret(String namespace, String name, Map<String, String> data) {
        return new SecretBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .addToData(data)
                .build();
    }

    public static class EndpointsModel extends AbstractSecureEndpointsModel {
        public EndpointsModel(EventStreams instance, SecurityComponentSpec spec, String componentName, String applicationName) {
            super(instance, componentName, applicationName);
            setTlsVersion(TlsVersion.TLS_V1_2);
            endpoints = createEndpoints(instance, spec);
            createService(EndpointServiceType.INTERNAL, Collections.emptyMap());
            createService(EndpointServiceType.ROUTE, Collections.emptyMap());
            createService(EndpointServiceType.NODE_PORT, Collections.emptyMap());
        }

        @Override
        protected List<Endpoint> createDefaultEndpoints(boolean authEnabled) {
            return Collections.singletonList(Endpoint.createDefaultExternalEndpoint(authEnabled));
        }

        @Override
        protected List<Endpoint> createP2PEndpoints(EventStreams instance) {
            List<Endpoint> endpoints = new ArrayList<>();
            endpoints.add(Endpoint.createP2PEndpoint(instance, Collections.emptyList(), Collections.singletonList(uniqueInstanceLabels())));
            return endpoints;
        }
    }

    public static KafkaListeners getMutualTLSOnBothInternalAndExternalListenerSpec() {

        return new KafkaListenersBuilder()
                .withNewKafkaListenerExternalRoute()
                    .withNewKafkaListenerAuthenticationTlsAuth()
                    .endKafkaListenerAuthenticationTlsAuth()
                .endKafkaListenerExternalRoute()
                .withNewTls()
                    .withNewKafkaListenerAuthenticationTlsAuth()
                    .endKafkaListenerAuthenticationTlsAuth()
                .endTls()
                .withNewPlain()
                .endPlain()
                .build();
    }

    public static KafkaListeners getMutualTLSOnInternalListenerSpec() {
        return new KafkaListenersBuilder()
                .withNewTls()
                    .withNewKafkaListenerAuthenticationTlsAuth()
                    .endKafkaListenerAuthenticationTlsAuth()
                .endTls()
                .build();
    }

    public static KafkaListeners getMutualTLSOnExternalListenerSpec() {
        return new KafkaListenersBuilder()
                .withNewKafkaListenerExternalRoute()
                    .withNewKafkaListenerAuthenticationTlsAuth()
                    .endKafkaListenerAuthenticationTlsAuth()
                .endKafkaListenerExternalRoute()
                .build();
    }

    public static KafkaListeners getMutualScramOnInternalListenerSpec() {
        return new KafkaListenersBuilder()
                .withNewTls()
                    .withNewKafkaListenerAuthenticationScramSha512Auth()
                    .endKafkaListenerAuthenticationScramSha512Auth()
                .endTls()
                .build();
    }

    public static KafkaListeners getMutualScramOnExternalListenerSpec() {
        return new KafkaListenersBuilder()
                .withNewKafkaListenerExternalRoute()
                    .withNewKafkaListenerAuthenticationScramSha512Auth()
                    .endKafkaListenerAuthenticationScramSha512Auth()
                .endKafkaListenerExternalRoute()
                .build();
    }


    public static KafkaListeners getServerAuthOnlyInternalListenerSpec() {
        return new KafkaListenersBuilder()
                .withNewTls()
                .endTls()
                .build();
    }

    public static KafkaListeners getServerAuthOnlyExternalListenerSpec() {
        return new KafkaListenersBuilder()
                .withNewKafkaListenerExternalRoute()
                .endKafkaListenerExternalRoute()
                .build();
    }

    public static KafkaListeners getNoSecurityListenerSpec() {
        return new KafkaListenersBuilder()
                .build();
    }

    public static void assertMeteringAnnotationsPresent(Map<String, String> annotations) {
        assertThat(annotations, hasKey(com.ibm.eventstreams.api.model.AbstractModel.PRODUCT_ID_KEY));
        assertThat(annotations, hasKey(com.ibm.eventstreams.api.model.AbstractModel.PRODUCT_NAME_KEY));
        assertThat(annotations, hasKey(com.ibm.eventstreams.api.model.AbstractModel.PRODUCT_METRIC_KEY));
        assertThat(annotations, hasKey(com.ibm.eventstreams.api.model.AbstractModel.PRODUCT_VERSION_KEY));
        assertThat(annotations, hasKey(com.ibm.eventstreams.api.model.AbstractModel.PRODUCT_CHARGED_CONTAINERS_KEY));
    }

    public static void assertEventStreamsLabelsPresent(Map<String, String> labels) {
        assertThat(labels, hasKey(Labels.STRIMZI_KIND_LABEL));
        assertThat(labels, hasKey(Labels.STRIMZI_CLUSTER_LABEL));
        assertThat(labels, hasKey(Labels.STRIMZI_NAME_LABEL));
    }
}
