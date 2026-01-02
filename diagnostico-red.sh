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
docker logs angular --tail 20
echo ""

echo "3. LOGS RECIENTES DEL BACKEND (últimas 20 líneas):"
echo "---------------------------------"
docker logs springboot --tail 20
echo ""

echo "4. ESTADÍSTICAS DE RED DE LOS CONTENEDORES:"
echo "---------------------------------"
docker stats --no-stream angular springboot mysql
echo ""

echo "5. PUERTOS ABIERTOS EN EL SERVIDOR:"
echo "---------------------------------"
netstat -tlnp | grep -E ':(80|8080|3307)'
echo ""

echo "6. CONEXIONES ACTIVAS AL PUERTO 80:"
echo "---------------------------------"
netstat -an | grep ':80 ' | grep ESTABLISHED | wc -l
echo " conexiones activas"
echo ""

echo "7. INFORMACIÓN DE RED DEL CONTENEDOR ANGULAR:"
echo "---------------------------------"
docker inspect angular | grep -A 10 "Networks"
echo ""

echo "8. HEALTHCHECK DEL FRONTEND:"
echo "---------------------------------"
docker inspect angular | grep -A 5 "Health"
echo ""

echo "9. PRUEBA DE CONECTIVIDAD INTERNA (angular -> springboot):"
echo "---------------------------------"
docker exec angular ping -c 3 springboot 2>/dev/null || echo "No se pudo hacer ping al backend"
echo ""

echo "10. PRUEBA DE CURL INTERNO (angular -> springboot:8080):"
echo "---------------------------------"
docker exec angular curl -s -o /dev/null -w "HTTP Status: %{http_code}\nTime: %{time_total}s\n" http://springboot:8080/api/eventos 2>/dev/null || echo "curl no disponible en el contenedor"
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
