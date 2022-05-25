# startup postgres
cd ./postgresql
kubectl apply -f ./deployment.yaml
kubectl apply -f ./service.yaml
cd ..

# startup keycloak
cd ./keycloak
kubectl apply -f ./deployment.yaml
kubectl apply -f ./service.yaml
cd ..

# provide localhost access
cd ./localhost
kubectl apply -f ./service.yaml
kubectl apply -f ./endpoint.yaml 
cd ..

# startup the sample
kubectl apply -f ./deployment.yaml
kubectl apply -f ./service.yaml

# finally configure ingress
kubectl create -f ./ingress-service.yaml

read -p "Done. Press any key ..."