#include <netinet/in.h>
#include <time.h>
#include <strings.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#define MAXLINE     4096    /* max text line length */
#define LISTENQ     1024    /* 2nd argument to listen() */
#define DAYTIME_PORT 3333

int encodeMessage();

int main(int argc, char **argv) {
    int     listenfd, connfd;
    struct sockaddr_in servaddr;
    char    buff[MAXLINE];
    time_t ticks;

    listenfd = socket(AF_INET, SOCK_STREAM, 0);

    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
    servaddr.sin_port = htons(DAYTIME_PORT); /* daytime server */

    bind(listenfd, (struct sockaddr *) &servaddr, sizeof(servaddr));
    listen(listenfd, LISTENQ);

    for ( ; ; ) {
        connfd = accept(listenfd, (struct sockaddr *) NULL, NULL);

        ticks = time(NULL);
        snprintf(buff, sizeof(buff), "%.24s\r\n", ctime(&ticks));
        encodeMessage();
        write(connfd, buff, strlen(buff));
        printf("Sending response: %s", buff);

        close(connfd);
    }
}

int encodeMessage() {
    FILE * whofd = popen("who", "r");
    
    char buffer[1000];
    int i = 0;
    while(fgets(buffer, 1000, whofd)) {
        printf("%c", buffer[i]);
        i++;
    }
    //printf("%s", buffer);
    return 0;
}

