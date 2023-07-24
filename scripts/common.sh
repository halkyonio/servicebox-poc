#!/usr/bin/env bash

set -e

shopt -s expand_aliases
alias k='kubectl'


# the speed to "type" the text
TYPE_SPEED=${TYPE_SPEED:-20}

# no wait after "p" or "pe"
NO_WAIT=${NO_WAIT:-"false"}

# if > 0, will pause for this amount of seconds before automatically proceeding with any p or pe
PROMPT_TIMEOUT=${PROMPT_TIMEOUT:-0}

# don't show command number unless user specifies it
SHOW_CMD_NUMS=false

if [[ "${NO_COLORS}" != "true" ]]
then
  # handy color vars for pretty prompts
  # Defining some colors for output
  NC='\033[0m' # No Color
  BLACK="\033[0;30m"
  BLUE='\033[0;34m'
  BROWN="\033[0;33m"
  GREEN='\033[0;32m'
  GREY="\033[0;90m"
  CYAN='\033[0;36m'
  MAGENTA='\033[0;35m'
  RED='\033[0;31m'
  PURPLE="\033[0;35m"
  WHITE='\033[0;37m'
  YELLOW='\033[0;33m'
  COLOR_RESET="\033[0m"
fi

C_NUM=0

# prompt and command color which can be overridden
DEMO_PROMPT="$ "
DEMO_CMD_COLOR=$WHITE
DEMO_COMMENT_COLOR=$GREY

shopt -s expand_aliases
alias k='kubectl'

####################################
## Section to declare the functions
####################################
repeat_char(){
  COLOR=${1}
	for i in {1..70}; do echo -ne "${!COLOR}$2${NC}"; done
}

fmt() {
  COLOR="WHITE"
  MSG="${@:1}"
  echo -e "${!COLOR} ${MSG}${NC}"
}

msg() {
  COLOR=${1}
  MSG="${@:2}"
  echo -e "\n${!COLOR}## ${MSG}${NC}"
}

succeeded() {
  echo -e "${GREEN}NOTE:${NC} $1"
}

note() {
  echo -e "${BLUE}NOTE:${NC} $1"
}

warn() {
  echo -e "${YELLOW}WARN:${NC} $1"
}

error() {
  echo -e "${RED}ERROR:${NC} $1"
}

log() {
  MSG="${@:2}"
  echo; repeat_char ${1} '#'; msg ${1} ${MSG}; repeat_char ${1} '#'; echo
}


function cmdExec() {
  COMMAND=${1}
  if [ "$CONTEXT" = "no-tty" ]; then
    set -x
    eval "${COMMAND}"
    exit 0
    set +x
  else
    if "$HAS_PV"; then
      pe "$1"
    else
      echo ""
      echo -e "${RED}##############################################################"
      echo "# Hold it !! I require pv but it's not installed. " >&2;
      echo -e "${RED}##############################################################"
      echo ""
      echo -e "${COLOR_RESET}Installing pv:"
      echo ""
      echo -e "${BLUE}Mac:${COLOR_RESET} $ brew install pv"
      echo ""
      echo -e "${BLUE}Other:${COLOR_RESET} http://www.ivarch.com/programs/pv.shtml"
      echo -e "${COLOR_RESET}"
      exit 1
    fi
  fi
}

####################################
## Play script as a demo functions section
####################################

##
# wait for user to press ENTER
# if $PROMPT_TIMEOUT > 0 this will be used as the max time for proceeding automatically
##
function wait() {
  if [[ "$PROMPT_TIMEOUT" == "0" ]]; then
    read -rs
  else
    read -rst "$PROMPT_TIMEOUT"
  fi
}

##
# render the prompt by itself
#
##
function pr() {
  # render the prompt
  x=$(PS1="$DEMO_PROMPT" "$BASH" --norc -i </dev/null 2>&1 | sed -n '${s/^\(.*\)exit$/\1/p;}')

  # show command number is selected
  if $SHOW_CMD_NUMS; then
   printf "[$((++C_NUM))] $x"
  else
   printf "$x"
  fi
}

##
# print command only. Useful for when you want to pretend to run a command
#
# takes 1 param - the string command to print
#
# usage: p "ls -l"
#
##
function p() {
  if [[ ${1:0:1} == "#" ]]; then
    cmd=$DEMO_COMMENT_COLOR$1$COLOR_RESET
  else
    cmd=$DEMO_CMD_COLOR$1$COLOR_RESET
  fi

  if [[ -z "$PROMPT_AFTER" ]]; then
    pr "$@"
  fi

  # wait for the user to press a key before typing the command
  if [ $NO_WAIT = false ]; then
    wait
  fi

  if [[ -z $TYPE_SPEED ]]; then
    echo -en "$cmd"
  else
    echo -en "$cmd" | pv -qL $[$TYPE_SPEED+(-2 + RANDOM%5)];
  fi

  # wait for the user to press a key before moving on
  if [ $NO_WAIT = false ]; then
    wait
  fi
  echo ""
}

##
# Prints and executes a command
#
# takes 1 parameter - the string command to run
#
# usage: pe "ls -l"
#
##
function pe() {
  # print the command
  p "$@"
  run_cmd "$@"
  if [[ -n "$PROMPT_AFTER" ]]; then
    pr
  fi
}

##
# print and executes a command immediately
#
# takes 1 parameter - the string command to run
#
# usage: pei "ls -l"
#
##
function pei {
  NO_WAIT=true pe "$@"
}

##
# Enters script into interactive mode
#
# and allows newly typed commands to be executed within the script
#
# usage : cmd
#
##
function cmd() {
  # render the prompt
  x=$(PS1="$DEMO_PROMPT" "$BASH" --norc -i </dev/null 2>&1 | sed -n '${s/^\(.*\)exit$/\1/p;}')
  printf "$x$COLOR_RESET"
  read command
  run_cmd "${command}"
}

function run_cmd() {
  function handle_cancel() {
    printf ""
  }

  trap handle_cancel SIGINT
  if [[ "$NO_COLORS" == "" ]]; then stty -echoctl ; fi
  eval "$@"
  if [[ "$NO_COLORS" == "" ]]; then stty echoctl ; fi
  trap - SIGINT
}


#
# handle some default params
# -h for help
# -d for disabling simulated typing
#
#while getopts ":dhncw:" opt; do
#  case $opt in
#    h)
#      usage
#      exit 1
#      ;;
#    d)
#      unset TYPE_SPEED
#      ;;
#    n)
#      NO_WAIT=true
#      ;;
#    c)
#      SHOW_CMD_NUMS=true
#      ;;
#    w)
#      PROMPT_TIMEOUT=$OPTARG
#      ;;
#  esac
#done

############################################
## Main section
############################################

# Define a function to check if a command exists
pv_exists() {
  command -v pv >/dev/null 2>&1
}

# Check if the command exists and store the result in a variable
if pv_exists; then
  HAS_PV=true
else
  HAS_PV=false
fi