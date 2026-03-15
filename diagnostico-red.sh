#!/bin/bash

echo "========================================="
echo "DIAGNÓSTICO DE RED - TAEMOI"
echo "========================================="
echo ""

echo "1. ESTADO DE LOS CONTENEDORES:"
echo "---------------------------------"
docker ps -a
echo ""

echo "2. LOGS RECIENTES DEL FRONTEND (últimas 20 líneas):"
echo "---------------------------------"
docker logs taemoi-frontend-prod --tail 20 2>/dev/null || docker logs angular --tail 20 2>/dev/null || echo "No se encontró contenedor frontend"
echo ""

echo "3. LOGS RECIENTES DEL BACKEND (últimas 20 líneas):"
echo "---------------------------------"
docker logs taemoi-backend-prod --tail 20 2>/dev/null || docker logs springboot --tail 20 2>/dev/null || echo "No se encontró contenedor backend"
echo ""

echo "4. ESTADÍSTICAS DE RED DE LOS CONTENEDORES:"
echo "---------------------------------"
docker stats --no-stream taemoi-frontend-prod taemoi-backend-prod taemoi-mysql-prod 2>/dev/null || docker stats --no-stream angular springboot mysql 2>/dev/null || echo "No se encontraron contenedores"
echo ""

echo "5. PUERTOS ABIERTOS EN EL SERVIDOR:"
echo "---------------------------------"
ss -tlnp 2>/dev/null | grep -E ':(80|8080|3307)' || netstat -tlnp 2>/dev/null | grep -E ':(80|8080|3307)' || echo "No se pudo ejecutar ss o netstat"
echo ""

echo "6. CONEXIONES ACTIVAS AL PUERTO 80:"
echo "---------------------------------"
CONNS=$(ss -an 2>/dev/null | grep ':80 ' | grep ESTAB | wc -l || netstat -an 2>/dev/null | grep ':80 ' | grep ESTABLISHED | wc -l || echo "0")
echo "$CONNS conexiones activas"
echo ""

echo "7. INFORMACIÓN DE RED DEL CONTENEDOR FRONTEND:"
echo "---------------------------------"
docker inspect taemoi-frontend-prod 2>/dev/null | grep -A 10 "Networks" || docker inspect angular 2>/dev/null | grep -A 10 "Networks" || echo "No se encontró contenedor frontend"
echo ""

echo "8. HEALTHCHECK DEL FRONTEND:"
echo "---------------------------------"
docker inspect taemoi-frontend-prod 2>/dev/null | grep -A 5 "Health" || docker inspect angular 2>/dev/null | grep -A 5 "Health" || echo "No se encontró contenedor frontend"
echo ""

echo "9. PRUEBA DE CONECTIVIDAD INTERNA (frontend -> backend):"
echo "---------------------------------"
docker exec taemoi-frontend-prod ping -c 3 taemoi-backend-prod 2>/dev/null || docker exec taemoi-frontend-prod ping -c 3 backend 2>/dev/null || docker exec angular ping -c 3 springboot 2>/dev/null || echo "No se pudo hacer ping al backend"
echo ""

echo "10. PRUEBA DE CURL INTERNO (frontend -> backend:8080):"
echo "---------------------------------"
docker exec taemoi-frontend-prod curl -s -o /dev/null -w "HTTP Status: %{http_code}\nTime: %{time_total}s\n" http://taemoi-backend-prod:8080/api/eventos 2>/dev/null || docker exec taemoi-frontend-prod curl -s -o /dev/null -w "HTTP Status: %{http_code}\nTime: %{time_total}s\n" http://backend:8080/api/eventos 2>/dev/null || echo "curl no disponible en el contenedor"
echo ""

echo "========================================="
echo "DIAGNÓSTICO COMPLETADO"
echo "========================================="
echo ""
echo "Si los logs del frontend no muestran peticiones cuando falla desde WiFi,"
echo "el problema está en la capa de red/router, no en Nginx."
echo ""
echo "Revisa:"
echo "  - Timeout de sesión NAT en tu router (debería ser > 300 segundos)"
echo "  - Límite de conexiones por dispositivo WiFi en el router"
echo "  - Cache ARP en el router"
echo "  - Firewall del router que pueda estar bloqueando conexiones persistentes"
