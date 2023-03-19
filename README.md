# Labo-1

Protocoles applicatifs mobiles

## Question Compression

Le temps que nous avons mesuré comprend la sérialisation, la compression (s'il y en une), l'envoi du
message, la réception du message, sa décompression (si nécessaire) ainsi que sa désérialisation. Il
est important de noter que le temps de traitement du serveur peut varier en fonction de sa charge.
Nous aurions pu mesurer le temps que prenaient uniquement les opérations de sérialisation, de
compression et d'envoi, mais nous avons préféré mesurer le temps total pour avoir une idée plus
précise de la performance de l'application.

Nous avons transmis les mêmes informations dans toutes les requêtes ci-dessous.

Temps mesuré en millisecondes :

|          | Compressé | Non compressé |
|----------|-----------|---------------|
| Json     | 340       | 63            |
| Xml      | 82        | 73            |
| Protobuf | 116       | 52            |

Volume de données transmis en octets :

|| Reçu - Compressé | Payload - Compressé | Reçu - Non compressé | Payload - Non compressé |          
|------------------|---------------------|----------------------|-------------------------|--------|
| Json             | 214                 | 614                  | 614                     | 614    |
| Xml              | 697                 | 3837                 | 3837                    | 3837   |
| Protobuf         | 132                 | 210                  | 210                     | 210    |

Nous pouvons voir que la compression a un impact sur le temps de traitement de la requête. En effet, le temps de traitement est plus long lorsque la requête est compressée. 
Cela est dû au fait que la compression prend du temps et que le serveur doit décompresser le message avant de le traiter. 

De plus, nous pouvons voir que le volume de données transmis est plus faible lorsque la requête est compressée.
En revanche, le volume du payload reste le même.
Cela est dû au fait que la compression permet de réduire la taille des données transmises, sans modifier le contenu du message.

## Question Firebase

Le token obtenu avec la méthode onNewToken du Service Firebase de réception des messages est une
chaîne de caractères unique qui identifie un appareil particulier auprès des serveurs Firebase Cloud
Messaging (FCM). Ce token est généré ou regénéré lorsque l'application s'enregistre pour recevoir
des notifications push via le service FCM.

Dans le cas d'un service de messagerie tel que WhatsApp, l'application doit enregistrer chaque
appareil de l'utilisateur pour recevoir des notifications push. Lorsqu'un nouvel utilisateur s'
inscrit, ou lorsqu'il installe l'application sur un nouvel appareil, l'application demande un
nouveau token pour chaque appareil auprès des serveurs FCM. Une fois que l'application a obtenu les
tokens pour chaque appareil, elle les associe aux identifiants de compte de l'utilisateur sur les
serveurs de l'application.

Lorsque l'utilisateur envoie un message à partir de l'une de ses applications sur l'un de ses
appareils, le serveur de l'application envoie une notification push aux autres appareils de l'
utilisateur. Lorsque l'application reçoit une notification push, elle peut afficher une alerte ou
une notification à l'utilisateur pour lui indiquer qu'un nouveau message est arrivé.

En ce qui concerne la gestion des tokens sur plusieurs appareils, chaque token est spécifique à un
appareil particulier, donc l'application doit enregistrer chaque token associé à chaque appareil
pour chaque utilisateur. Lorsqu'un utilisateur utilise plusieurs appareils, l'application doit s'
assurer que les messages sont envoyés à tous les appareils associés à l'utilisateur. Pour ce faire,
l'application peut stocker les tokens de chaque appareil de l'utilisateur sur les serveurs de
l'application, de sorte que les notifications push puissent être envoyées à tous les appareils.

## 1.5.Question théorique d’approfondissement
Pour améliorer l'api GraphQL, on pourrait compresser les données retournée et/ou utiliser protobuf.
Cela permtrait de rendre la taille des données envoyée encore plus petite. Cependant le principale problème
que l'on va rencontrer d'après nous, est la complexitée coté serveur. Il faudra donc avoir un schema protobuf
pour chaque possibilité ce qui est très peu pratique.
