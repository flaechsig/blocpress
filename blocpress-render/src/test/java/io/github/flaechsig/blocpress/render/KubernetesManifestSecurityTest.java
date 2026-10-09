package io.github.flaechsig.blocpress.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-0051/REQ-0052: Die Kubernetes-Manifeste betreiben jeden blocpress-Container ohne root,
 * ohne Rechteausweitung und mit schreibgeschuetztem Dateisystem. Ein blocpress-Pod ist einer,
 * der ein Image {@code flaechsig/blocpress-*} startet; PostgreSQL und Elasticsearch sind
 * ausgenommen (US-0058).
 */
class KubernetesManifestSecurityTest {

    @ParameterizedTest(name = "{displayName} [{0}]")
    @DisplayName("REQ-0051: everyBlocpressContainerHasRestrictiveSecurityContext")
    @ValueSource(strings = {
            "../deploy/k8s/app/render.yaml",
            "../deploy/k8s/app/workbench.yaml",
            "../deploy/k8s/app/studio.yaml",
            "../docs/guides/examples/blocpress-render-k8s.yaml"})
    void everyBlocpressContainerHasRestrictiveSecurityContext(String manifest) throws Exception {
        for (Map<String, Object> pod : blocpressPods(manifest)) {
            Map<String, Object> podContext = map(pod.get("securityContext"));
            for (Map<String, Object> container : containers(pod)) {
                String where = manifest + " / " + container.get("name");
                Map<String, Object> context = map(container.get("securityContext"));

                assertEquals(true, inherited(context, podContext, "runAsNonRoot"), where + ": runAsNonRoot");
                Object uid = inherited(context, podContext, "runAsUser");
                if (uid != null) {
                    assertNotEquals(0, uid, where + ": runAsUser 0");
                }
                assertEquals(false, context.get("allowPrivilegeEscalation"), where + ": allowPrivilegeEscalation");
                Map<String, Object> capabilities = map(context.get("capabilities"));
                assertTrue(list(capabilities.get("drop")).contains("ALL"), where + ": capabilities.drop [ALL]");
                assertNull(capabilities.get("add"), where + ": capabilities.add");
                Object seccomp = map(inherited(context, podContext, "seccompProfile")).get("type");
                assertEquals("RuntimeDefault", seccomp, where + ": seccompProfile");
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @DisplayName("REQ-0052: rootFilesystemReadOnlyAndWritableOnlyViaEmptyDir")
    @ValueSource(strings = {
            "../deploy/k8s/app/render.yaml",
            "../deploy/k8s/app/workbench.yaml",
            "../deploy/k8s/app/studio.yaml",
            "../docs/guides/examples/blocpress-render-k8s.yaml"})
    void rootFilesystemReadOnlyAndWritableOnlyViaEmptyDir(String manifest) throws Exception {
        for (Map<String, Object> pod : blocpressPods(manifest)) {
            Map<String, Map<String, Object>> volumes = new HashMap<>();
            for (Object v : list(pod.get("volumes"))) {
                volumes.put((String) map(v).get("name"), map(v));
            }
            for (Map<String, Object> container : containers(pod)) {
                String where = manifest + " / " + container.get("name");
                assertEquals(true, map(container.get("securityContext")).get("readOnlyRootFilesystem"),
                        where + ": readOnlyRootFilesystem");
                for (Object m : list(container.get("volumeMounts"))) {
                    Map<String, Object> mount = map(m);
                    if (Boolean.TRUE.equals(mount.get("readOnly"))) {
                        continue;
                    }
                    Map<String, Object> volume = volumes.get(mount.get("name"));
                    assertNotNull(volume, where + ": Volume " + mount.get("name") + " fehlt");
                    assertTrue(volume.containsKey("emptyDir"),
                            where + ": " + mount.get("mountPath") + " ist beschreibbar, aber kein emptyDir");
                }
            }
        }
    }

    /** Pod-Spezifikationen aller Deployments der Datei, die ein blocpress-Image starten. */
    private static List<Map<String, Object>> blocpressPods(String manifest) throws Exception {
        List<Map<String, Object>> pods = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Path.of(manifest))) {
            for (Object doc : new Yaml().loadAll(reader)) {
                Map<String, Object> resource = map(doc);
                if (!"Deployment".equals(resource.get("kind"))) {
                    continue;
                }
                Map<String, Object> pod = map(map(map(resource.get("spec")).get("template")).get("spec"));
                boolean blocpress = list(pod.get("containers")).stream()
                        .map(c -> String.valueOf(map(c).get("image")))
                        .anyMatch(image -> image.startsWith("flaechsig/blocpress-"));
                if (blocpress) {
                    pods.add(pod);
                }
            }
        }
        assertFalse(pods.isEmpty(), manifest + " enthaelt keinen blocpress-Pod");
        return pods;
    }

    /** Init-Container und Container eines Pods. */
    private static List<Map<String, Object>> containers(Map<String, Object> pod) {
        return Stream.concat(list(pod.get("initContainers")).stream(), list(pod.get("containers")).stream())
                .map(KubernetesManifestSecurityTest::map)
                .toList();
    }

    /** Wert aus dem securityContext des Containers, sonst aus dem des Pods. */
    private static Object inherited(Map<String, Object> container, Map<String, Object> pod, String key) {
        return container.containsKey(key) ? container.get(key) : pod.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }
}
