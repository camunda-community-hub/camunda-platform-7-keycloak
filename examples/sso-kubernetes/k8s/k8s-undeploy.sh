# remove ingress configuration
kubectl delete -f ./ingress-service.yaml

# delete the sample
kubectl delete -f ./deployment.yaml
kubectl delete -f ./service.yaml

# delete keycloak
cd ./keycloak
kubectl delete -f ./deployment.yaml
kubectl delete -f ./service.yaml
cd ..

# delete postgres
cd ./postgresql
kubectl delete -f ./deployment.yaml
kubectl delete -f ./service.yaml
cd ..
