FROM quay.io/operator-framework/upstream-registry-builder:v1.5.6 as builder
COPY olm-catalog manifests
RUN ./bin/initializer -o ./bundles.db
FROM scratch
COPY --from=builder /build/bundles.db /bundles.db
COPY --from=builder /build/bin/registry-server /registry-server
COPY --from=builder /bin/grpc_health_probe /bin/grpc_health_probe
EXPOSE 50051
ENTRYPOINT ["/registry-server"]
CMD ["--database", "bundles.db"]