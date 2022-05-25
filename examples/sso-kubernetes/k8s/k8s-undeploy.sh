# remove ingress configuration
kubectl delete -f ./ingress-service.yaml

# delete the sample
kubectl delete -f ./deployment.yaml
kubectl delete -f ./service.yaml

# delete localhost access
cd ./localhost
kubectl delete -f ./service.yaml
kubectl delete -f ./endpoint.yaml 
cd ..

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
