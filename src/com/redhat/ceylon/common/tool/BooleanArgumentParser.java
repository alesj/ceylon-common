package com.redhat.ceylon.common.tool;

class BooleanArgumentParser implements ArgumentParser<Boolean> {

    @Override
    public Boolean parse(String argument) {
        return argument.matches("1|yes|true");
    }

}
