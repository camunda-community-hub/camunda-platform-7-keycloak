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

# startup redis
#cd ./redis
#kubectl apply -f ./deployment.yaml
#kubectl apply -f ./service.yaml
#cd ..

# startup the sample
kubectl apply -f ./deployment.yaml
kubectl apply -f ./service.yaml

# finally configure ingress
kubectl create -f ./ingress-service.yaml