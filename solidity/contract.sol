// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

contract ISTCoin {
address private constant CLIENT1 = 0xB17E7374589312676B90229AdB4CE6E58552e223;
address private constant CLIENT2 = 0x7023496DA3Bd9F2F8908D1e2aC32641CD819d3E3;
address private constant CLIENT3 = 0x7a7ED76326308dc41CbbD79FC7827d4BE46B1A39;
    uint256 private constant INITIAL_SUPPLY = 10000000000;
    uint256 private constant CLIENT_INITIAL_IST = 10000;
    uint256 private constant CLIENT_INITIAL_UNITS = CLIENT_INITIAL_IST * 100;

    string public name = "IST Coin";
    string public symbol = "IST";
    uint8 public decimals = 2;
    uint256 private _totalSupply;

    mapping(address => uint256) private _balances;
    mapping(address => mapping(address => uint256)) private _allowances;

    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);

    constructor() {
        // 100.000.000 * 100 (2 decimals)
        _totalSupply = INITIAL_SUPPLY;

        // Initialize only client accounts 
        _balances[CLIENT1] = 0;
        _balances[CLIENT2] = 0;
        _balances[CLIENT3] = 0;

        // PreAllocate the allowances
        _allowances[CLIENT1][CLIENT2] = 0;
        _allowances[CLIENT1][CLIENT3] = 0;
        _allowances[CLIENT2][CLIENT1] = 0;
        _allowances[CLIENT2][CLIENT3] = 0;
        _allowances[CLIENT3][CLIENT1] = 0;
        _allowances[CLIENT3][CLIENT2] = 0;

        // Initial balance for each client
        _balances[CLIENT1] = CLIENT_INITIAL_UNITS;
        _balances[CLIENT2] = CLIENT_INITIAL_UNITS;
        _balances[CLIENT3] = CLIENT_INITIAL_UNITS;

        emit Transfer(address(0), CLIENT1, CLIENT_INITIAL_UNITS);
        emit Transfer(address(0), CLIENT2, CLIENT_INITIAL_UNITS);
        emit Transfer(address(0), CLIENT3, CLIENT_INITIAL_UNITS);

        uint256 distributed = CLIENT_INITIAL_UNITS * 3;
        require(_totalSupply >= distributed, "Initial distribution exceeds supply");
        _totalSupply -= distributed;
    }

    function totalSupply() public view returns (uint256) {
       return _totalSupply;
    }

    function balanceOf(address _owner) public view returns (uint256) {
        return _balances[_owner];
    }

    function transfer(address _to, uint256 _value) public returns (bool success) {
        //require(_balances[msg.sender] >= _value, "Not enough balance");
        if(_balances[msg.sender] < _value) {
            return false;
        }

        _balances[msg.sender] -= _value;
        _balances[_to] += _value;

        emit Transfer(msg.sender, _to, _value);

        return true;
    }

    function transferFrom(address _from, address _to, uint256 _value) public returns (bool success) {
        //require(_balances[_from] >= _value, "Not enough balance");
        //require(_value <= _allowances[_from][msg.sender], "Allowance denied");
        if (_balances[_from] < _value) {
            return false;
        }
        if (_value > _allowances[_from][msg.sender]) {
            return false;
        }

        _balances[_from] -= _value;
        _balances[_to] += _value;

        _allowances[_from][msg.sender] -= _value;

        emit Transfer(_from, _to, _value);

        return true;
    }

    function approve(address _spender, uint256 _value) public returns (bool success) {
        //require(_value == 0 || _allowances[msg.sender][_spender] == 0, "Reset allowance to 0 first");
        if(_value != 0 || _allowances[msg.sender][_spender] != 0){
            return false;
        }
        _allowances[msg.sender][_spender] = _value;
        emit Approval(msg.sender, _spender, _value);

        return true;
    }

    function increaseAllowance(address spender, uint256 addedValue) public returns (bool) {
        _allowances[msg.sender][spender] += addedValue;
        emit Approval(msg.sender, spender, _allowances[msg.sender][spender]);
        return true;
    }

    function decreaseAllowance(address spender, uint256 subtractedValue) public returns (bool) {
        uint256 currentAllowance = _allowances[msg.sender][spender];
        //require(currentAllowance >= subtractedValue, "Decreased allowance below zero");
        if(currentAllowance <= subtractedValue){
            return false;
        }
        
        _allowances[msg.sender][spender] = currentAllowance - subtractedValue;
        emit Approval(msg.sender, spender, _allowances[msg.sender][spender]);
        return true;
    }

    function allowance(address _owner, address _spender) public view returns (uint256 remaining) {
        return _allowances[_owner][_spender];
    }

}
