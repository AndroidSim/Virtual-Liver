#! /usr/bin/Rscript
####! /bin/bash setR

###
## Average the reaction field output from several monte carlo trials and write to a file.
##
## -gepr 2017-05-05
###

##dev.off()

argv <- commandArgs(TRUE)

if (length(argv) <= 0) {
    print(paste("Usage: rxnfield.r <exp directories>"))
    print("  Requires there be multiple files below the exp directory")
    print("  with a common name and common headers for columns. e.g.:")
    print("  ./2014-04-07-1645/rxnprod-dPV-00[0-9][0-9].csv")
    quit()
}

source("~/R/misc.r")

## for each experiment
for (d in argv) {

  for (dir in c("dPV","dCV")) {

    print (paste("Averaging",dir,"reaction field for ",d))

    ## get all the enzymes files in that experiment
    pattern <- paste("rxnprod", dir, "[0-9]+.csv",sep="-")
    files <- list.files(path = d, pattern = pattern, recursive = TRUE)
    compname <- unlist(strsplit(files[1],"-"))[1]

    ## loop over trial files
    if (exists("whole")) rm(whole)
    trial <- 1
    for (f in files) {

        data <- read.csv(file = paste(d, f, sep="/"), colClasses = "numeric")

        if (exists("whole")) {
          whole <- pad1stColumns(whole, data)
          padded.data <- pad1stColumns(data,whole)
          endC <- ncol(whole)
          whole[,2:endC] <- whole[,2:endC] + padded.data[,2:endC]
        } else {
          whole <- data
        }

        trial <- trial + 1
    }
    endC <- ncol(whole)
    whole[,2:endC] <- whole[,2:endC]/trial

    ## write the data to a file
    write.csv(x=whole, file=paste(d, "_", compname,"-",dir,".csv", sep=""), row.names=FALSE)

  } ## end loop over directions

}

#q()

