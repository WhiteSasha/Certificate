#!/bin/bash
# Set the color variable
green='\033[0;32m'
yellow='\033[0;33m'
# Clear the color after that
clear='\033[0m'

echo -e "\n---------------------------------------------------"
echo -e "${green}\t --==   Update let's encrypt certificate   ==-- ${clear}"
echo -e "\n---------------------------------------------------"


echo -e "${yellow}* Remove Nginx host config ${clear}"
rm /etc/nginx/sites-enabled/nexus.alex-white.ru

echo -e "${yellow}* Create new link ${clear}"
ln -s /etc/nginx/sites-available/nexus.alex-white.ru:for_letsencrypt /etc/nginx/sites-enabled/nexus.alex-white.ru:for_letsencrypt

echo -e "${yellow}* Reload Nginx config ${clear}"
sudo nginx -s reload

echo -e "${yellow}* Read site ${clear}"
sleep 1
curl http://nexus.alex-white.ru/

echo -e "${yellow}* TEST dry run renew let's encrypt${clear}"
certbot certonly --dry-run --webroot -w /var/www/nexus -d nexus.alex-white.ru

echo -e "${green}* !! Renew let's encrypt !!${clear}"
#certbot certonly --webroot -w /var/www/nexus -d nexus.alex-white.ru

#check result

echo -e "${yellow}* Remove let's encrypt Nginx config file${clear}"
rm /etc/nginx/sites-enabled/nexus.alex-white.ru:for_letsencrypt

echo -e "${yellow}* Create new link${clear}"
ln -s /etc/nginx/sites-available/nexus.alex-white.ru /etc/nginx/sites-enabled/nexus.alex-white.ru

echo -e "${yellow}* Reload Nginx config${clear}"
nginx -s reload

echo -e "${yellow}* Check SSL Certificate on site${clear}"
sleep 1
curl --insecure -vvI https://nexus.alex-white.ru 2>&1 | awk 'BEGIN { cert=0 } /^\* SSL connection/ { cert=1 } /^\*/ { if (cert) print }'	


echo -e "${yellow} ***WELL DONE***${clear}"