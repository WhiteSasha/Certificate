#!/bin/bash
domain="$1"

# Set the color variable
green='\033[0;32m'
yellow='\033[0;33m'
# Clear the color after that
clear='\033[0m'

echo -e "\n---------------------------------------------------------"
echo -e "${green}\t --==   Update let's encrypt certificate   ==-- ${clear}"
echo -e "\tUpdate certificate for domain: ${domain}"
echo -e "\n---------------------------------------------------------"


sleep 2

echo -e "${yellow}* Remove Nginx host config ${clear}"
rm /etc/nginx/sites-enabled/${domain}

echo -e "${yellow}* Create new link ${clear}"
ln -s /etc/nginx/sites-available/${domain}:for_letsencrypt /etc/nginx/sites-enabled/${domain}:for_letsencrypt

echo -e "${yellow}* Reload Nginx config ${clear}"
nginx -s reload

echo -e "${yellow}* Read site ${clear}"
sleep 5
curl http://${domain}

echo -e "${yellow}* TEST dry run renew let's encrypt${clear}"
certbot certonly --dry-run --webroot -w /var/www/${domain} -d ${domain}

echo -e "${green}* !! Renew let's encrypt !!${clear}"
#certbot certonly --webroot -w /var/www/${domain} -d ${domain}.alex-white.ru

#check result

echo -e "${yellow}* Remove let's encrypt Nginx config file${clear}"
rm /etc/nginx/sites-enabled/${domain}:for_letsencrypt

echo -e "${yellow}* Create new link${clear}"
ln -s /etc/nginx/sites-available/${domain} /etc/nginx/sites-enabled/${domain}

echo -e "${yellow}* Reload Nginx config${clear}"
nginx -s reload

echo -e "${yellow}* Check SSL Certificate on site${clear}"
sleep 1
curl --insecure -vvI https://${domain} 2>&1 | awk 'BEGIN { cert=0 } /^\* SSL connection/ { cert=1 } /^\*/ { if (cert) print }'	


echo -e "${yellow} ***---- WELL DONE ----***${clear}"